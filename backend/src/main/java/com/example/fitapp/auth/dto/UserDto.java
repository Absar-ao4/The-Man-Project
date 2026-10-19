package com.example.fitapp.auth.dto;

import com.example.fitapp.auth.Plan;
import com.example.fitapp.auth.Role;
import java.util.UUID;

public record UserDto(UUID id, String email, String username, Plan plan, Role role) {}
