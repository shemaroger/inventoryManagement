package com.company.tai.user.controller;

import com.company.tai.common.dto.ApiResponse;
import com.company.tai.user.dto.CreateUserRequest;
import com.company.tai.user.dto.UpdateUserRequest;
import com.company.tai.user.dto.UserDto;
import com.company.tai.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_VIEW')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<List<UserDto>> listUsers() {
        return ApiResponse.ok(userService.listUsers());
    }

    @GetMapping("/{id}")
    public ApiResponse<UserDto> getUser(@PathVariable Long id) {
        return ApiResponse.ok(userService.getUser(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGE')")
    public ApiResponse<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok("User created", userService.createUser(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGE')")
    public ApiResponse<UserDto> updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok("User updated", userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGE')")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.ok("User deleted", null);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGE')")
    public ApiResponse<UserDto> activate(@PathVariable Long id) {
        return ApiResponse.ok("User activated", userService.setActive(id, true));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGE')")
    public ApiResponse<UserDto> deactivate(@PathVariable Long id) {
        return ApiResponse.ok("User deactivated", userService.setActive(id, false));
    }
}
