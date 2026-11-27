import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Alert,
  ConfigProvider,
  Form,
  Input,
  Button,
  Spin,
  message,
  theme,
} from "antd";
import type { ThemeConfig } from "antd";
import {
  LockOutlined,
  UserOutlined,
  SafetyCertificateOutlined,
  EyeOutlined,
  EyeInvisibleOutlined,
} from "@ant-design/icons";
import { useTranslation } from "react-i18next";
import apiClient from "../../services/api";
import { useAuth, type AuthUser } from "@/contexts/AuthContext";

interface LoginForm {
  username: string;
  password: string;
  confirmPassword?: string;
  displayName?: string;
}

// The public login keeps its own light palette without changing workspace preferences.
const loginTheme: ThemeConfig = {
  inherit: false,
  algorithm: theme.defaultAlgorithm,
  token: {
    colorPrimary: "#087f9c",
    colorPrimaryHover: "#066b84",
    colorText: "#172331",
    colorTextSecondary: "#526b78",
    colorTextPlaceholder: "#607080",
    colorBgContainer: "#ffffff",
    colorBorder: "#c8d6df",
    colorError: "#b42318",
    colorErrorBg: "#fff2f0",
    colorErrorBorder: "#f1b7b0",
    colorWarning: "#946000",
    colorWarningBg: "#fff8e8",
    colorWarningBorder: "#ead39a",
    colorInfo: "#087f9c",
    colorInfoBg: "#f0f8fb",
    colorInfoBorder: "#b5dce7",
    borderRadius: 8,
    controlHeightLG: 44,
    fontSize: 14,
    fontSizeLG: 14,
    fontFamily: 'Aptos, "Microsoft YaHei UI", "Noto Sans SC", sans-serif',
  },
  components: {
    Button: { primaryShadow: "none", fontWeight: 600 },
  },
};

export default function LoginPage() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const [checkingBootstrap, setCheckingBootstrap] = useState(true);
  const [bootstrapRequired, setBootstrapRequired] = useState(false);
  const [bootstrapCheckFailed, setBootstrapCheckFailed] = useState(false);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { login } = useAuth();
  const sessionExpired = searchParams.get("reason") === "session_expired";

  const checkBootstrap = () => {
    setCheckingBootstrap(true);
    setBootstrapCheckFailed(false);
    apiClient
      .get("/api/v1/auth/bootstrap/status")
      .then(({ data }) => setBootstrapRequired(Boolean(data.bootstrapRequired)))
      .catch(() => setBootstrapCheckFailed(true))
      .finally(() => setCheckingBootstrap(false));
  };

  useEffect(() => {
    checkBootstrap();
  }, []);

  const handleLogin = async (values: LoginForm) => {
    if (bootstrapCheckFailed) {
      return;
    }
    setLoading(true);
    try {
      if (bootstrapRequired) {
        await apiClient.post("/api/v1/auth/bootstrap", {
          username: values.username,
          password: values.password,
          displayName: values.displayName,
        });
        setBootstrapRequired(false);
      }
      const { data } = await apiClient.post("/api/v1/auth/login", values);
      if (data.token) {
        const user: AuthUser = {
          id: data.user?.id ?? 0,
          username: data.user?.username ?? values.username,
          displayName: data.user?.displayName,
          email: data.user?.email,
          roles: data.user?.roles ?? [],
          customerId: data.user?.customerId || undefined,
          tenantId: data.user?.tenantId || undefined,
        };
        login(data.token, data.refreshToken, user);
        message.success(
          t("login.welcome", { name: user.displayName || user.username }),
        );
        navigate("/overview", { replace: true });
      } else {
        message.error(t("login.errorInvalidCredentials"));
      }
    } catch {
      // error interceptor in api.ts already shows message
    } finally {
      setLoading(false);
    }
  };

  const renderPasswordIcon = (visible: boolean) => (
    <button
      type="button"
      aria-label={t(visible ? "login.hidePassword" : "login.showPassword")}
      aria-pressed={visible}
      onKeyDown={(event) => {
        if (event.key === " " || event.key === "Enter") {
          event.preventDefault();
          event.stopPropagation();
          if (event.repeat) return;
          const button = event.currentTarget;
          // AntD focuses the input when its suffix is clicked; keep keyboard focus here.
          button.click();
          button.focus({ preventScroll: true });
        }
      }}
    >
      {visible ? (
        <EyeOutlined aria-hidden="true" />
      ) : (
        <EyeInvisibleOutlined aria-hidden="true" />
      )}
    </button>
  );

  return (
    // INTERACTION-011: login readiness = bootstrap status check settled
    // (success or failure); not visible to users or assistive technology.
    <main
      className="tds-login"
      data-app-ready={checkingBootstrap ? "false" : "true"}
      data-route-path="/login"
    >
      <ConfigProvider theme={loginTheme}>
        <section
          className="tds-login__card"
          aria-labelledby="tds-login-form-title"
        >
          <header className="tds-login__brand">
            <div className="tds-login__decoration" aria-hidden="true" />
            <span className="tds-login__brand-mark" aria-hidden="true">
              <SafetyCertificateOutlined />
            </span>
            <div className="tds-login__brand-copy">
              <h1>{t("login.title")}</h1>
              <p>{t("login.subtitle")}</p>
            </div>
            <span className="tds-login__brand-rule" aria-hidden="true" />
          </header>

          <div className="tds-login__panel">
            <div className="tds-login__form-wrap">
              <header className="tds-login__form-header">
                <h2 id="tds-login-form-title">
                  {bootstrapRequired
                    ? t("login.createAdmin")
                    : t("login.login")}
                </h2>
              </header>

              <Form<LoginForm>
                name="login"
                onFinish={handleLogin}
                autoComplete="off"
                layout="vertical"
                size="large"
              >
                {sessionExpired && (
                  <Alert
                    type="warning"
                    showIcon
                    message={t("login.sessionExpired")}
                    style={{ marginBottom: 16, textAlign: "left" }}
                  />
                )}
                {bootstrapCheckFailed && (
                  <Alert
                    type="error"
                    showIcon
                    message={t("login.platformNotReady")}
                    action={
                      <Button size="small" onClick={checkBootstrap}>
                        {t("login.retry")}
                      </Button>
                    }
                    style={{ marginBottom: 16, textAlign: "left" }}
                  />
                )}
                {bootstrapRequired && (
                  <Alert
                    type="info"
                    showIcon
                    message={t("login.bootstrapTitle")}
                    description={t("login.bootstrapDescription")}
                    style={{ marginBottom: 16, textAlign: "left" }}
                  />
                )}
                {bootstrapRequired && (
                  <Form.Item name="displayName" label={t("login.displayName")}>
                    <Input
                      placeholder={t("login.displayName")}
                      aria-label={t("login.displayName")}
                    />
                  </Form.Item>
                )}
                <Form.Item
                  name="username"
                  label={t("login.username")}
                  rules={[
                    {
                      required: true,
                      message: t("login.validationUsernameRequired"),
                    },
                    ...(bootstrapRequired
                      ? [
                          {
                            pattern: /^[A-Za-z0-9_.-]{3,64}$/,
                            message: t("login.validationBootstrapUsername"),
                          },
                        ]
                      : []),
                  ]}
                >
                  <Input
                    prefix={<UserOutlined />}
                    placeholder={t("login.validationUsernameRequired")}
                    aria-label={t("login.username")}
                    spellCheck={false}
                    autoComplete={bootstrapRequired ? "username" : "username"}
                  />
                </Form.Item>

                <Form.Item
                  name="password"
                  label={t("login.password")}
                  rules={[
                    {
                      required: true,
                      message: t("login.validationPasswordRequired"),
                    },
                    ...(bootstrapRequired
                      ? [
                          {
                            min: 8,
                            max: 16,
                            message: t("login.validationBootstrapPassword"),
                          },
                        ]
                      : []),
                  ]}
                >
                  <Input.Password
                    iconRender={renderPasswordIcon}
                    prefix={<LockOutlined />}
                    placeholder={t("login.validationPasswordRequired")}
                    aria-label={t("login.password")}
                    autoComplete={
                      bootstrapRequired ? "new-password" : "current-password"
                    }
                  />
                </Form.Item>

                {bootstrapRequired && (
                  <Form.Item
                    name="confirmPassword"
                    label={t("login.confirmPassword")}
                    dependencies={["password"]}
                    rules={[
                      {
                        required: true,
                        message: t("login.validationConfirmPassword"),
                      },
                      ({ getFieldValue }) => ({
                        validator(_, value) {
                          return !value || getFieldValue("password") === value
                            ? Promise.resolve()
                            : Promise.reject(
                                new Error(
                                  t("login.validationPasswordMismatch"),
                                ),
                              );
                        },
                      }),
                    ]}
                  >
                    <Input.Password
                      iconRender={renderPasswordIcon}
                      prefix={<LockOutlined />}
                      placeholder={t("login.validationConfirmPassword")}
                      aria-label={t("login.confirmPassword")}
                      autoComplete="new-password"
                    />
                  </Form.Item>
                )}

                <Form.Item className="tds-login__submit">
                  <Button
                    type="primary"
                    htmlType="submit"
                    aria-label={
                      bootstrapRequired
                        ? t("login.createAdmin")
                        : t("login.login")
                    }
                    aria-busy={loading || checkingBootstrap}
                    loading={loading || checkingBootstrap}
                    disabled={bootstrapCheckFailed}
                    block
                  >
                    {bootstrapRequired
                      ? t("login.createAdmin")
                      : t("login.login")}
                  </Button>
                </Form.Item>
              </Form>

              {checkingBootstrap && (
                <div role="status" aria-live="polite">
                  <Spin size="small" />
                </div>
              )}
            </div>
          </div>
        </section>
      </ConfigProvider>
    </main>
  );
}
