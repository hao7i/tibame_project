import type { ReactNode } from "react";

/**
 * The four 11x11 registration marks that sit outside a blueprint frame.
 * The design system requires them on every framed element — cards, framed
 * figures, primary buttons — so they are never optional.
 *
 * Put this inside any element carrying the `blueprint` class.
 */
export function BlueprintCorners() {
  return (
    <>
      <i className="corner tl" aria-hidden="true" />
      <i className="corner tr" aria-hidden="true" />
      <i className="corner bl" aria-hidden="true" />
      <i className="corner br" aria-hidden="true" />
    </>
  );
}

type BlueprintProps = {
  className?: string;
  children?: ReactNode;
};

/** A div wearing the blueprint frame, corners included. */
export function Blueprint({ className, children }: BlueprintProps) {
  return (
    <div className={className ? `blueprint ${className}` : "blueprint"}>
      {children}
      <BlueprintCorners />
    </div>
  );
}
