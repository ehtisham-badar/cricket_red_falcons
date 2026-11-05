package com.redfalcons.ledger.web;

import com.redfalcons.ledger.model.UserAccount;
import com.redfalcons.ledger.service.AccountService;
import com.redfalcons.ledger.service.ManagerAccessService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Which squad player is asking. A player's sign-in token decides it (the account's linked player), so the
 * player id a browser sends (X-Viewer) is only trusted from the admin and managers, as before. Every endpoint
 * that reads X-Viewer (photos, profile, votes, a private ledger) then sees the right player, or none.
 */
@Component
public class AccountFilter extends OncePerRequestFilter {

    public static final String USER_HEADER = "X-User-Token";
    /** The signed-in account, for controllers that need it. */
    public static final String ACCOUNT = "rf.account";

    private final AccountService accounts;
    private final AdminAuth auth;
    private final ManagerAccessService managers;

    public AccountFilter(AccountService accounts, AdminAuth auth, ManagerAccessService managers) {
        this.accounts = accounts;
        this.auth = auth;
        this.managers = managers;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String userToken = request.getHeader(USER_HEADER);
        String viewer = null;
        if (userToken != null && !userToken.isBlank()) {
            Optional<UserAccount> a = accounts.resolve(userToken);
            if (a.isPresent()) {
                request.setAttribute(ACCOUNT, a.get());
                viewer = accounts.playerOf(a.get()).orElse(null);
            }
        } else if (auth.isValidToken(request.getHeader(LedgerController.TOKEN_HEADER))
                || managers.find(request.getHeader(ManagerController.MANAGER_HEADER)).isPresent()) {
            viewer = request.getHeader(LedgerController.VIEWER_HEADER);
        }
        chain.doFilter(new ViewerRequest(request, viewer), response);
    }

    /** The request with X-Viewer set to the player decided above (or removed). */
    private static final class ViewerRequest extends HttpServletRequestWrapper {
        private final String viewer;

        ViewerRequest(HttpServletRequest request, String viewer) {
            super(request);
            this.viewer = viewer == null || viewer.isBlank() ? null : viewer;
        }

        private static boolean isViewer(String name) {
            return LedgerController.VIEWER_HEADER.equalsIgnoreCase(name);
        }

        @Override
        public String getHeader(String name) {
            return isViewer(name) ? viewer : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (!isViewer(name)) return super.getHeaders(name);
            return viewer == null ? Collections.emptyEnumeration() : Collections.enumeration(Set.of(viewer));
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>();
            Collections.list(super.getHeaderNames()).forEach(n -> { if (!isViewer(n)) names.add(n); });
            if (viewer != null) names.add(LedgerController.VIEWER_HEADER);
            return Collections.enumeration(names);
        }
    }
}
