import { useCallback, useRef, useState } from 'react';

/**
 * Synchronous ref lock prevents two clicks in the same render frame from
 * launching the same mutation before React can disable the button.
 */
export default function useSubmitGuard() {
  const lockedRef = useRef(false);
  const [submitting, setSubmitting] = useState(false);

  const begin = useCallback(() => {
    if (lockedRef.current) return false;
    lockedRef.current = true;
    setSubmitting(true);
    return true;
  }, []);

  const end = useCallback(() => {
    lockedRef.current = false;
    setSubmitting(false);
  }, []);

  return { submitting, begin, end };
}
