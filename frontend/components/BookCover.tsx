"use client";

import { useState } from "react";
import styles from "./BookCover.module.css";

/**
 * 書封, or the 佔位框 when there is none.
 *
 * The image is hotlinked straight from the 通路 CDN that published it — we do
 * not copy or re-host it, so it stays their asset and their bandwidth. That is
 * also why next/image is not used here: its optimizer would fetch and cache a
 * copy on our own server, which is precisely what we are not doing.
 *
 * A client component for one reason: covers rot. When the URL 404s the reader
 * gets the 佔位框 back rather than a broken-image icon, and that can only be
 * known in the browser.
 */
export function BookCover({
  src,
  title,
  className,
}: {
  src?: string;
  title: string;
  className: string;
}) {
  const [failed, setFailed] = useState(false);
  const showImage = Boolean(src) && !failed;

  return (
    <div className={`blueprint duotone ${className}`}>
      {showImage ? (
        /* next/image would fetch and cache a copy of someone elses asset on
           our server; this is deliberately a hotlink, so the rule is off. */
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={src}
          alt={`${title} 封面`}
          className={styles.image}
          loading="lazy"
          decoding="async"
          referrerPolicy="no-referrer"
          onError={() => setFailed(true)}
        />
      ) : (
        <span className={styles.label}>封面</span>
      )}
    </div>
  );
}
