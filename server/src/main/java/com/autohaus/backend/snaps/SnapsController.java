package com.autohaus.backend.snaps;

import com.autohaus.backend.snaps.dto.SnapRequestDto;
import com.autohaus.backend.snaps.dto.SnapResponseDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/snaps")
public class SnapsController {

    private final SnapsService snapsService;

    public SnapsController(SnapsService snapsService) {
        this.snapsService = snapsService;
    }

    @PostMapping
    public List<SnapResponseDto> getSnaps(@Valid @RequestBody SnapRequestDto request) {
        return snapsService.getSnaps(request);
    }
}
