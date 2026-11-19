package edu.investigacion.web;

import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Models.User;
import edu.investigacion.domain.Repositories.UserRepository;
import edu.investigacion.domain.Security.DecodedToken;
import edu.investigacion.domain.Security.TokenService;
import io.javalin.http.Context;

import java.util.Optional;

public final class AuthMiddleware {
    private final TokenService tokenService;
    private final UserRepository userRepository;

    public AuthMiddleware(TokenService tokenService, UserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    public boolean authenticate(Context ctx) {
        String header = ctx.header("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            ctx.status(401).json(JsonRenderer.renderError(new AppError.Unauthorized("Token de autorización ausente")));
            return false;
        }

        String token = header.substring(7).trim();
        Optional<DecodedToken> decodedOpt = tokenService.verify(token);
        if (decodedOpt.isEmpty()) {
            ctx.status(401).json(JsonRenderer.renderError(new AppError.Unauthorized("Token inválido o expirado")));
            return false;
        }

        DecodedToken decoded = decodedOpt.get();
        Optional<User> userOpt = userRepository.findById(decoded.userId());
        if (userOpt.isEmpty() || userOpt.get().state() != edu.investigacion.domain.Enums.State.ACTIVE) {
            ctx.status(401).json(JsonRenderer.renderError(new AppError.Unauthorized("Usuario inactivo o no encontrado")));
            return false;
        }

        ctx.attribute("currentUser", userOpt.get());
        return true;
    }

    public boolean requireAdmin(Context ctx) {
        if (!authenticate(ctx)) return false;
        User user = ctx.attribute("currentUser");
        if (user == null || user.role() != Role.ADMIN) {
            ctx.status(403).json(JsonRenderer.renderError(new AppError.Forbidden("Se requieren permisos de administrador")));
            return false;
        }
        return true;
    }
}
