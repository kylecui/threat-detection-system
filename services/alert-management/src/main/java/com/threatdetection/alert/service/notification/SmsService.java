package com.threatdetection.alert.service.notification;

/**
 * SMS服务接口
 */
public interface SmsService {

    /**
     * 发送SMS消息
     * @param phoneNumber 手机号码
     * @param message 消息内容
     * @return 发送结果
     */
    SmsResult sendSms(String phoneNumber, String message);

    /**
     * 检查服务是否可用
     * @return true if available
     */
    boolean isAvailable();

    /**
     * SMS发送结果
     */
    class SmsResult {
        private final boolean success;
        private final String messageId;
        private final String errorMessage;

        public SmsResult(boolean success, String messageId, String errorMessage) {
            this.success = success;
            this.messageId = messageId;
            this.errorMessage = errorMessage;
        }

        public static SmsResult success(String messageId) {
            return new SmsResult(true, messageId, null);
        }

        public static SmsResult failure(String errorMessage) {
            return new SmsResult(false, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessageId() {
            return messageId;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}