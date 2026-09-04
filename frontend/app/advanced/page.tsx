import { listChannels } from "@/lib/api";
import { AdvancedForm } from "./AdvancedForm";

/**
 * 進階搜尋.
 *
 * The 通路 list is fetched here rather than hard-coded into the form: 限定通路 has
 * to name the same six 通路 the rest of the site does, and they live in the
 * 書目, not in the front end.
 */
export default async function AdvancedPage() {
  const channels = await listChannels();

  return <AdvancedForm channels={channels} />;
}
