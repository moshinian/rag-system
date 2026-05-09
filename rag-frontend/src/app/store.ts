import { create } from "zustand";

type AppStore = {
  currentKbCode?: string;
  setCurrentKbCode: (kbCode?: string) => void;
};

export const useAppStore = create<AppStore>((set) => ({
  currentKbCode: undefined,
  setCurrentKbCode: (kbCode) => set({ currentKbCode: kbCode })
}));
