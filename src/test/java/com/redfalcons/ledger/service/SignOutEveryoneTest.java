package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.AdminSession;
import com.redfalcons.ledger.model.ManagerSession;
import com.redfalcons.ledger.model.UserSession;
import com.redfalcons.ledger.repo.AdminSessionRepository;
import com.redfalcons.ledger.repo.ManagerSessionRepository;
import com.redfalcons.ledger.repo.SettingRepository;
import com.redfalcons.ledger.repo.UserSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** The switch to accounts signs everyone out once; later restarts leave sessions alone. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:signout",
        "ledger.admin-key=test-key",
        "ledger.sync-from=",
        "ledger.hpl-url="
})
class SignOutEveryoneTest {

    @Autowired SignOutEveryone signOut;
    @Autowired SettingRepository settings;
    @Autowired AdminSessionRepository admins;
    @Autowired ManagerSessionRepository managers;
    @Autowired UserSessionRepository users;

    private void someoneSignedIn() {
        admins.save(new AdminSession("a1", "Chrome", "2026-11-05T10:00:00Z"));
        managers.save(new ManagerSession("m1", "mgr", "2026-11-05T10:00:00Z"));
        users.save(new UserSession("u1", "user", "Safari", "2026-11-05T10:00:00Z", "2026-12-05T10:00:00Z"));
    }

    @Test
    void signsEveryoneOutOnceOnly() {
        // It already ran when the app started; pretend it hasn't.
        settings.deleteById(SignOutEveryone.DONE);
        someoneSignedIn();
        signOut.once();
        assertThat(admins.count() + managers.count() + users.count()).isZero();
        assertThat(settings.existsById(SignOutEveryone.DONE)).isTrue();

        // Next restart: people who signed in again stay signed in.
        someoneSignedIn();
        signOut.once();
        assertThat(admins.count()).isEqualTo(1);
        assertThat(managers.count()).isEqualTo(1);
        assertThat(users.count()).isEqualTo(1);
    }
}
