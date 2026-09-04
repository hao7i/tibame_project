"use client";

import { useRef, useState, useActionState } from "react";
import { register, signIn, type AuthResult } from "@/lib/auth";
import styles from "./login.module.css";

/**
 * 會員登入 and 註冊新帳號, in one form.
 *
 * The design draws 註冊新帳號 as a link beside 忘記密碼 rather than a second
 * screen, so the two share these fields and only the submitted action differs.
 * Both are Server Actions, which means the form still submits with no client
 * JavaScript and the session never passes through the browser.
 */
export function LoginForm({ hint, pendingIsbn }: { hint: string; pendingIsbn?: string }) {
  const [mode, setMode] = useState<"signIn" | "register">("signIn");

  const [signInError, signInAction, signingIn] = useActionState<AuthResult, FormData>(
    signIn,
    undefined,
  );
  const [registerError, registerAction, registering] = useActionState<AuthResult, FormData>(
    register,
    undefined,
  );

  const isRegister = mode === "register";
  const action = isRegister ? registerAction : signInAction;
  const error = isRegister ? registerError : signInError;
  const pending = isRegister ? registering : signingIn;

  // 空白欄位 warning. The form carries noValidate so this 繁中 message replaces
  // the browser bubble, which is worded in the browser locale and cannot be
  // styled; the required attributes stay for assistive technology.
  const warningRef = useRef<HTMLDialogElement>(null);
  const [warning, setWarning] = useState("");
  const missingFieldRef = useRef<"email" | "password">("email");

  const guardBlankFields = (event: React.FormEvent<HTMLFormElement>) => {
    const form = event.currentTarget;
    const email = (form.elements.namedItem("email") as HTMLInputElement).value.trim();
    const password = (form.elements.namedItem("password") as HTMLInputElement).value;

    const missing: string[] = [];
    if (!email) {
      missing.push("電子郵件");
    }
    if (!password) {
      missing.push("密碼");
    }
    if (missing.length === 0) {
      return;
    }

    event.preventDefault();
    // Sending the reader back to the first empty field is the whole point of
    // saying which one it is.
    missingFieldRef.current = email ? "password" : "email";
    setWarning(`請填寫${missing.join("與")}後再${isRegister ? "註冊" : "登入"}。`);
    warningRef.current?.showModal();
  };

  const dismissWarning = () => {
    warningRef.current?.close();
    document.getElementById(missingFieldRef.current)?.focus();
  };

  return (
    <>
      <form className={styles.form} action={action} onSubmit={guardBlankFields} noValidate>
        {/* Rides along so 登入 can finish the 追蹤 the reader started. */}
        {pendingIsbn ? <input type="hidden" name="pendingIsbn" value={pendingIsbn} /> : null}

        <h3 className={styles.formTitle}>{isRegister ? "註冊新帳號" : "會員登入"}</h3>
        <p className={styles.hint}>{isRegister ? "密碼至少 8 個字元。" : hint}</p>

        {/* The failure is stated where the reader is looking, not as an alert
            they have to dismiss before they can correct anything. */}
        {error ? (
          <p className={styles.error} role="alert">
            {error.error}
          </p>
        ) : null}

        <div className="field">
          <label htmlFor="email">電子郵件</label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            required
            className={`input ${styles.input}`}
            placeholder="you@example.com"
          />
        </div>

        <div className="field">
          <label htmlFor="password">密碼</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete={isRegister ? "new-password" : "current-password"}
            required
            minLength={8}
            className={`input ${styles.input}`}
            placeholder="至少 8 個字元"
          />
        </div>

        <label className={`radio ${styles.remember}`}>
          <input type="checkbox" name="remember" />
          <span className="dot" aria-hidden="true" />
          記住此裝置
        </label>

        <button
          type="submit"
          className={`btn btn-primary btn-block ${styles.submit}`}
          disabled={pending}
        >
          {pending ? "處理中…" : isRegister ? "註冊" : "登入"}
        </button>

        <div className={styles.links}>
          <a href="#">忘記密碼</a>
          <button
            type="button"
            className={styles.linkButton}
            onClick={() => setMode(isRegister ? "signIn" : "register")}
          >
            {isRegister ? "已經有帳號了" : "註冊新帳號"}
          </button>
        </div>
      </form>

      {/* A native dialog so Esc closes it and focus stays inside, rather than a
          div that only looks like a 彈窗. */}
      <dialog ref={warningRef} className={`dialog ${styles.warning}`} aria-labelledby="warning-title">
        <p id="warning-title" className="dialog-title">
          尚未填寫完整
        </p>
        <p className="dialog-body">{warning}</p>
        <div className="dialog-actions">
          <button type="button" className="btn btn-primary" onClick={dismissWarning}>
            知道了
          </button>
        </div>
      </dialog>
    </>
  );
}
