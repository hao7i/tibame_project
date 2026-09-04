/**
 * Is this string an ISBN-13.
 *
 * Mirrors Isbn13 on the backend, and deliberately so: the server is the one that
 * decides how a 收錄 term is routed, but the screen has to know whether offering
 * the 找書 button would be honest. An 進階搜尋 on the ISBN 欄位 with a number that
 * is not an ISBN can only ever come back empty, and a button that cannot work is
 * worse than no button.
 */
export function isIsbn13(value: string): boolean {
  if (!/^97[89]\d{10}$/.test(value)) {
    return false;
  }

  let sum = 0;
  for (let i = 0; i < 12; i += 1) {
    const digit = value.charCodeAt(i) - 48;
    sum += i % 2 === 0 ? digit : digit * 3;
  }
  return (10 - (sum % 10)) % 10 === value.charCodeAt(12) - 48;
}
