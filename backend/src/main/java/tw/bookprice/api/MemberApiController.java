package tw.bookprice.api;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tw.bookprice.member.MemberService;
import tw.bookprice.member.dto.MemberView;
import tw.bookprice.member.dto.RegisterRequest;

/**
 * 註冊 and the signed-in 會員.
 *
 * 登入 and 登出 are not here: they are Spring Security form login and logout at
 * /api/session, handled by the filter chain before any controller is reached.
 * Putting a hand-written login endpoint beside them would mean two ways to
 * start a session, only one of which the security configuration knows about.
 */
@RestController
public class MemberApiController {

    private final MemberService memberService;

    public MemberApiController(MemberService memberService) {
        this.memberService = memberService;
    }

    /** 註冊新帳號. 201 with the new 會員, or 409 when the 電子郵件 is taken. */
    @PostMapping("/api/members")
    public ResponseEntity<MemberView> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memberService.register(request));
    }

    /**
     * The signed-in 會員 — what the 導覽列 renders 登入 or 登出 from.
     *
     * Under /api/me, so the security configuration refuses it with 401 when
     * nobody is signed in rather than answering with an empty 會員.
     */
    @GetMapping("/api/me")
    public MemberView current(Principal principal) {
        return memberService.findByEmail(principal.getName());
    }
}
