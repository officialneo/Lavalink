package lavalink.server.util;

import com.sedmelluq.lava.extensions.youtuberotator.planner.AbstractRoutePlanner;
import com.sedmelluq.lava.extensions.youtuberotator.tools.Tuple;
import com.sedmelluq.lava.extensions.youtuberotator.tools.ip.IpBlock;
import org.apache.http.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public class RotatingIpv4RoutePlanner extends AbstractRoutePlanner {

    private static final Logger log = LoggerFactory.getLogger(RotatingIpv4RoutePlanner.class);
    private final Predicate<InetAddress> ipFilter;
    private final AtomicBoolean next;
    private final AtomicReference<BigInteger> index;
    private volatile InetAddress lastFailingAddress;
    private final List<IpBlock> ipBlocks;

    /**
     * @param ipBlocks the block to perform balancing over.
     */
    public RotatingIpv4RoutePlanner(final List<IpBlock> ipBlocks) {
        this(ipBlocks, i -> true);
    }

    /**
     * @param ipBlocks the block to perform balancing over.
     * @param ipFilter function to filter out certain IP addresses picked from the IP block, causing another random to be chosen.
     */
    public RotatingIpv4RoutePlanner(final List<IpBlock> ipBlocks, final Predicate<InetAddress> ipFilter) {
        this(ipBlocks, ipFilter, true);
    }

    /**
     * @param ipBlocks            the block to perform balancing over.
     * @param ipFilter            function to filter out certain IP addresses picked from the IP block, causing another random to be chosen.
     * @param handleSearchFailure whether a search 429 should trigger the ip as failing
     */
    public RotatingIpv4RoutePlanner(final List<IpBlock> ipBlocks, final Predicate<InetAddress> ipFilter, final boolean handleSearchFailure) {
        super(ipBlocks, handleSearchFailure);
        this.ipBlocks = ipBlocks;
        this.ipFilter = ipFilter;
        this.next = new AtomicBoolean(false);
        this.index = new AtomicReference<>(BigInteger.valueOf(0));
        this.lastFailingAddress = null;
    }

    public void next() {
        if (!this.next.compareAndSet(false, true)) {
            log.warn("Call to next() even when previous next() hasn't completed yet");
        }
    }

    public InetAddress getCurrentAddress() {
        return ipBlocks.get(index.get().intValue()).getAddressAtIndex(0);
    }

    public BigInteger getIndex() {
        return index.get();
    }

    @Override
    protected Tuple<InetAddress, InetAddress> determineAddressPair(final Tuple<Inet4Address, Inet6Address> remoteAddresses) throws HttpException {
        InetAddress currentAddress = null;
        InetAddress remoteAddress;
        if (ipBlock.getType() == Inet4Address.class) {
            if (remoteAddresses.l != null) {
                if (next.get()) {
                    currentAddress = extractLocalAddress();
                    log.info("Selected " + currentAddress.toString() + " as new outgoing ip");
                }
                remoteAddress = remoteAddresses.l;
            } else {
                throw new HttpException("Could not resolve host");
            }
        } else {
            throw new HttpException("Unsupported block type");
        }

        if (currentAddress == null) {
            currentAddress = ipBlocks.get(index.get().intValue()).getAddressAtIndex(0);
        }
        next.set(false);
        return new Tuple<>(currentAddress, remoteAddress);
    }

    @Override
    protected void onAddressFailure(final InetAddress address) {
        if (lastFailingAddress != null && lastFailingAddress.toString().equals(address.toString())) {
            log.warn("Address {} was already failing, not triggering next()", address.toString());
            return;
        }
        lastFailingAddress = address;
        next();
    }

    private InetAddress extractLocalAddress() {
        InetAddress localAddress;
        do {
            int position = index.get().intValue();
            if (ipBlocks.size() > 1) {
                if (position >= (ipBlocks.size() - 1)) {
                    index.set(BigInteger.ZERO);
                    throw new RuntimeException("Can't find a free ip");
                }
                position = index.accumulateAndGet(BigInteger.ONE, BigInteger::add).intValue();
            }
            localAddress = ipBlocks.get(position).getAddressAtIndex(0);

            if (ipBlocks.size() == 1 && isUnavailableAddress(localAddress)) {
                throw new RuntimeException("Can't find a free ip");
            }
        } while (isUnavailableAddress(localAddress));
        return localAddress;
    }

    private boolean isUnavailableAddress(InetAddress address) {
        return address == null || !ipFilter.test(address) || !isValidAddress(address);
    }
}
