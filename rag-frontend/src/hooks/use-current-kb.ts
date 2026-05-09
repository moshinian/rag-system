import { useEffect } from "react";
import { useParams } from "react-router-dom";
import { useAppStore } from "../app/store";

export function useCurrentKb() {
  const params = useParams();
  const currentKbCode = useAppStore((state) => state.currentKbCode);
  const setCurrentKbCode = useAppStore((state) => state.setCurrentKbCode);

  useEffect(() => {
    if (params.kbCode && params.kbCode !== currentKbCode) {
      setCurrentKbCode(params.kbCode);
    }
  }, [currentKbCode, params.kbCode, setCurrentKbCode]);

  return params.kbCode ?? currentKbCode;
}
