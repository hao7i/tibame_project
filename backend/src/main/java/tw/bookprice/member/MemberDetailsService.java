package tw.bookprice.member;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How Spring Security loads a 前台會員 for form login.
 *
 * Every account it can return carries ROLE_MEMBER and nothing else, so this
 * service can never produce an administrator: /admin is guarded by its own
 * filter chain with its own, entirely separate, account.
 */
@Service
@Transactional(readOnly = true)
public class MemberDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    public MemberDetailsService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        Member member = memberRepository.findByEmail(MemberService.normalise(email))
                // The message deliberately does not say whether the 電子郵件 exists:
                // the 登入 form answers 帳號或密碼不正確 either way, so a stranger
                // cannot use it to find out who has an account here.
                .orElseThrow(() -> new UsernameNotFoundException("帳號或密碼不正確"));

        return User.withUsername(member.getEmail())
                .password(member.getPasswordHash())
                .roles("MEMBER")
                .build();
    }
}
