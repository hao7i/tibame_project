import { redirect } from "next/navigation";
import { currentMember } from "@/lib/session";
import { LoginForm } from "./LoginForm";
import styles from "./login.module.css";

/** The 會員功能 the dark column advertises, per the design. */
const BENEFITS = [
  "追蹤清單同步各裝置",
  "目標價達成 email 通知",
  "保留搜尋條件與比價紀錄",
];

type LoginPageProps = {
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>;
};

export default async function LoginPage({ searchParams }: LoginPageProps) {
  // Already signed in: this screen has nothing to offer, so it does not sit
  // there inviting a second 登入.
  if (await currentMember()) {
    redirect("/");
  }

  const params = await searchParams;
  const pending = params.book;

  // The design gives this line two forms: the plain invitation, and the one
  // shown when the reader pressed 追蹤 first and was sent here.
  const hint =
    typeof pending === "string" && pending
      ? `登入後即可追蹤《${pending}》的價格並收到降價通知。`
      : "追蹤清單為會員功能，登入後才能使用。";

  return (
    <div className={styles.layout}>
      <aside className={styles.pitch}>
        <div>
          <h6 className={styles.kicker}>會員功能</h6>
          <h2 className={styles.pitchHeading}>登入後可追蹤書價並收到降價通知</h2>
        </div>
        <ul className={styles.benefits}>
          {BENEFITS.map((benefit) => (
            <li key={benefit}>{benefit}</li>
          ))}
        </ul>
      </aside>

      <div className={styles.formColumn}>
        <LoginForm hint={hint} />
      </div>
    </div>
  );
}
