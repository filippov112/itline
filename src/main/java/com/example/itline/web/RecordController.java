package com.example.itline.web;

import com.example.itline.domain.Record;
import com.example.itline.service.RecordService;
import com.example.itline.web.dto.CreateRecordRequest;
import com.example.itline.web.dto.RecordDto;
import com.example.itline.web.dto.UpdateRecordRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST-контракт для статического клиента.
 * Никакой бизнес-логики — только HTTP ↔ сервис.
 */
@RestController
@RequestMapping("/api/records")
public class RecordController {

    private final RecordService service;

    public RecordController(RecordService service) {
        this.service = service;
    }

    /** GET /api/records -> 200 + список. */
    @GetMapping
    public List<RecordDto> list() {
        return service.findAll().stream().map(RecordDto::of).toList();
    }

    /** GET /api/records/{id} -> 200 + запись, либо 404 (через advice). */
    @GetMapping("/{id}")
    public RecordDto get(@PathVariable("id") Long id) {
        // findById в сервисе бросит NotFoundException -> 404 через advice
        return RecordDto.of(service.findById(id));
    }

    /** POST /api/records -> 201 + созданная запись. */
    @PostMapping
    public ResponseEntity<RecordDto> create(@Valid @RequestBody CreateRecordRequest req) {
        Record created = service.create(req);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Location", "/api/records/" + created.getId())
                .body(RecordDto.of(created));
    }

    /** PUT /api/records/{id} -> 200 + обновлённая, либо 404/409 через advice. */
    @PutMapping("/{id}")
    public RecordDto update(@PathVariable("id") Long id,
                            @Valid @RequestBody UpdateRecordRequest req) {
        return RecordDto.of(service.update(id, req));
    }

    /**
     * DELETE /api/records/{id}
     * version передаём query-параметром, а не телом:
     * DELETE с телом поддерживается не всеми прокси/клиентами.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id,
                                       @RequestParam("version") Long version) {
        service.delete(id, version);
        return ResponseEntity.noContent().build();
    }
}