"use client";

import { useActionState, useState } from "react";
import { BlueprintCorners } from "@/components/Blueprint";
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
export function LoginForm({ hint }: { hint: string }) {
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

  return (
    <form className={styles.form} action={action}>
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
        className={`btn btn-primary blueprint btn-block ${styles.submit}`}
        disabled={pending}
      >
        {pending ? "處理中…" : isRegister ? "註冊" : "登入"}
        <BlueprintCorners />
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
  );
}
