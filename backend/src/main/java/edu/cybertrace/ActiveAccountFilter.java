package edu.cybertrace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Ends old sessions immediately when an account is deactivated or its role changes. */
class ActiveAccountFilter extends OncePerRequestFilter {
    private final Users users;
    private final SecurityContextRepository contextRepository;

    ActiveAccountFilter(Users users, SecurityContextRepository contextRepository) {
        this.users = users;
        this.contextRepository = contextRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var context = SecurityContextHolder.getContext();
        var authentication = context.getAuthentication();
        if (request.getRequestURI().startsWith("/api/") && authentication != null
                && authentication.isAuthenticated() && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName())) {
            var account = users.findByUsername(authentication.getName()).filter(user -> user.enabled);
            if (account.isEmpty()) {
                context.setAuthentication(null);
                contextRepository.saveContext(context, request, response);
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "This account is inactive. Sign in with an active account.");
                return;
            }
            var user = account.get();
            if (!authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + user.role))) {
                var principal = User.withUsername(user.username).password(user.password).roles(user.role).build();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
                contextRepository.saveContext(context, request, response);
            }
        }
        chain.doFilter(request, response);
    }
}
