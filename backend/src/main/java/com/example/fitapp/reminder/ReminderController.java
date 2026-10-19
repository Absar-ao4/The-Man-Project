package com.example.fitapp.reminder;

import com.example.fitapp.auth.CurrentUser;
import com.example.fitapp.reminder.ReminderDtos.ReminderDto;
import com.example.fitapp.reminder.ReminderDtos.ReminderRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/reminders")
@Tag(name = "Reminders")
public class ReminderController {

    private final ReminderService service;

    public ReminderController(ReminderService service) {
        this.service = service;
    }

    /** The reminders you have changed; any type not listed is on its default (off). */
    @GetMapping
    public List<ReminderDto> list() {
        return service.list(CurrentUser.id());
    }

    @PutMapping("/{type}")
    public ReminderDto put(@PathVariable ReminderType type, @Valid @RequestBody ReminderRequest req) {
        return service.put(CurrentUser.id(), type, req);
    }

    /** Back to the default for this type. */
    @DeleteMapping("/{type}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@PathVariable ReminderType type) {
        service.reset(CurrentUser.id(), type);
    }
}
