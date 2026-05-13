import { Alert } from "antd";
import { getErrorMessage } from "../../hooks/use-api-error";

type ApiErrorAlertProps = {
  error: unknown;
};

/** 渲染复用组件。 */
export function ApiErrorAlert({ error }: ApiErrorAlertProps) {
  return <Alert type="error" showIcon message={getErrorMessage(error)} />;
}
