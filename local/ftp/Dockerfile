# 로컬 FTP: 과제 제공 FTP와 같은 권한(목록 조회·신규 업로드만)을 재현한다 (D-14).
FROM alpine:3.23.7

RUN apk add --no-cache vsftpd

COPY vsftpd.conf /etc/vsftpd/vsftpd.conf
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 21 21100-21110

ENTRYPOINT ["/entrypoint.sh"]
