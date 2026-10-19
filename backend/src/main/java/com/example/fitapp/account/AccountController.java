package com.example.fitapp.account;

import com.example.fitapp.auth.CurrentUser;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Account")
public class AccountController {

    public record DeleteRequest(@NotBlank String password) {}

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    /** Everything the account holds, as a JSON download. */
    @GetMapping("/export")
    public ResponseEntity<ObjectNode> export() {
        ObjectNode body = service.export(CurrentUser.id());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("the-man-project-export.json").build().toString())
                .body(body);
    }

    /** Permanently deletes the account and all its data. Needs the current password. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Valid @RequestBody DeleteRequest req) {
        service.delete(CurrentUser.id(), req.password());
    }
}
