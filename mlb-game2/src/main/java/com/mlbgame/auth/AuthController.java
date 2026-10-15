package com.mlbgame.auth;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    public String loginPage(@AuthenticationPrincipal AuthUser authUser) {
        return authUser != null ? "redirect:/dashboard" : "auth/login";
    }

    @GetMapping("/signup")
    public String signupPage(@AuthenticationPrincipal AuthUser authUser, @ModelAttribute("form") SignupForm form) {
        return authUser != null ? "redirect:/dashboard" : "auth/signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("form") SignupForm form, BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/signup";
        }
        try {
            authService.signup(form);
        } catch (DuplicateUserException e) {
            bindingResult.rejectValue(e.getField(), "duplicate", e.getMessage());
            return "auth/signup";
        }
        redirectAttributes.addFlashAttribute("signupSuccess", true);
        return "redirect:/login";
    }
}
