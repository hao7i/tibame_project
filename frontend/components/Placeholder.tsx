import { Blueprint } from "@/components/Blueprint";
import styles from "./Placeholder.module.css";

type PlaceholderProps = {
  /** The screen name as it appears in the design, e.g. 追蹤清單. */
  title: string;
  /** Which ticket builds this screen, so the stub explains itself. */
  builtIn: string;
};

/**
 * Stub for a route the global chrome links to but whose screen is built in a
 * later ticket. Keeps navigation honest instead of leaving dead links.
 */
export function Placeholder({ title, builtIn }: PlaceholderProps) {
  return (
    <div className={styles.page}>
      <Blueprint className="card">
        <p className="card-kicker">尚未建置</p>
        <p className="card-title">{title}</p>
        <p className="card-body">此畫面於「{builtIn}」該張票中建置。</p>
      </Blueprint>
    </div>
  );
}
