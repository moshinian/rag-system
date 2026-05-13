import { ConfigProvider, App as AntApp, theme } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "react-router-dom";
import type { ComponentProps } from "react";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 10_000
    }
  }
});

type AppProvidersProps = {
  router: ComponentProps<typeof RouterProvider>["router"];
};

/** 组装全局主题、QueryClient 和路由 Provider。 */
export function AppProviders({ router }: AppProvidersProps) {
  return (
    <ConfigProvider
      theme={{
        algorithm: theme.defaultAlgorithm,
        token: {
          colorPrimary: "#1f6feb",
          colorBgLayout: "#f4f7fb",
          colorBgContainer: "#ffffff",
          colorText: "#172033",
          borderRadius: 14,
          fontFamily:
            "\"IBM Plex Sans\", \"PingFang SC\", \"Microsoft YaHei\", sans-serif"
        }
      }}
    >
      <QueryClientProvider client={queryClient}>
        <AntApp>
          <RouterProvider router={router} />
        </AntApp>
      </QueryClientProvider>
    </ConfigProvider>
  );
}
