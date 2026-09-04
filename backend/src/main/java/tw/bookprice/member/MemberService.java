package tw.bookprice.member;

import java.time.Instant;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bookprice.member.dto.MemberView;
import tw.bookprice.member.dto.RegisterRequest;

/**
 * 會員 商業邏輯 — 註冊 and the lookups 登入 needs.
 *
 * The plaintext password reaches this class and goes no further: it is hashed
 * here and the hash is what the entity is constructed with, so no layer below
 * ever holds the original.
 */
@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 註冊新帳號.
     *
     * @throws EmailAlreadyRegisteredException when the 電子郵件 is already an 帳號
     */
    @Transactional
    public MemberView register(RegisterRequest request) {
        String email = normalise(request.email());

        if (memberRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        Member member = memberRepository.save(new Member(
                email,
                passwordEncoder.encode(request.password()),
                Instant.now()));

        return new MemberView(member.getId(), member.getEmail());
    }

    /** The signed-in 會員, for the front end to render 導覽列 state from. */
    public MemberView findByEmail(String email) {
        return memberRepository.findByEmail(normalise(email))
                .map(member -> new MemberView(member.getId(), member.getEmail()))
                .orElseThrow(() -> new java.util.NoSuchElementException("找不到會員: " + email));
    }

    /**
     * 電子郵件 is a case-insensitive identifier in practice, so it is stored and
     * looked up lower-cased. Without this, Alice@example.com and
     * alice@example.com would be two accounts that look identical on screen.
     *
     * Public because it is a rule about the identifier itself, not about this
     * service: anything keying on 電子郵件 has to apply the same one.
     */
    public static String normalise(String email) {
        return (email == null) ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
