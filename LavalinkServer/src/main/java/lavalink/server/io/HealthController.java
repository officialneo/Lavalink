package lavalink.server.io;

import io.prometheus.client.exporter.common.TextFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Nullable;
import java.io.IOException;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping(produces = TextFormat.CONTENT_TYPE_004)
    public ResponseEntity<String> getMetrics(@Nullable @RequestParam(name = "name[]", required = false) String[] includedParam)
            throws IOException {
        return new ResponseEntity<>("OK", HttpStatus.OK);
    }
}
