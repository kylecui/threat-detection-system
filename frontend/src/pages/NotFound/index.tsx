import { ArrowLeftOutlined } from "@ant-design/icons";
import { Button } from "antd";
import { useTranslation } from "react-i18next";
import { useLocation, useNavigate } from "react-router-dom";

export default function NotFound() {
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <section className="tds-not-found" aria-labelledby="not-found-title">
      <div className="tds-not-found__code" aria-hidden="true">
        404
      </div>
      <div className="tds-not-found__content">
        <span className="tds-not-found__eyebrow">
          {t("notFound.eyebrow")}
        </span>
        <h2 id="not-found-title">{t("notFound.title")}</h2>
        <p>{t("notFound.description")}</p>
        <dl className="tds-not-found__path">
          <dt>{t("notFound.requestedPath")}</dt>
          <dd>{location.pathname}</dd>
        </dl>
        <Button
          type="primary"
          icon={<ArrowLeftOutlined />}
          onClick={() => navigate("/overview")}
        >
          {t("notFound.backOverview")}
        </Button>
      </div>
    </section>
  );
}
