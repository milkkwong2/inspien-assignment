#!/bin/sh
set -eu

FTP_USER="${FTP_USER:-recruit}"
FTP_PASSWORD="${FTP_PASSWORD:?FTP_PASSWORD is required}"
FTP_UPLOAD_PATH="${FTP_UPLOAD_PATH:-Recruit/2026}"
PASV_ADDRESS="${PASV_ADDRESS:-127.0.0.1}"

HOME_DIR="/home/${FTP_USER}"

if ! id "${FTP_USER}" >/dev/null 2>&1; then
	adduser -D -h "${HOME_DIR}" -s /bin/sh "${FTP_USER}"
fi
echo "${FTP_USER}:${FTP_PASSWORD}" | chpasswd

mkdir -p "${HOME_DIR}/${FTP_UPLOAD_PATH}"
chown -R "${FTP_USER}:${FTP_USER}" "${HOME_DIR}"

# 공용 폴더 재현: 다른 지원자가 올린 파일을 미리 둔다
SEED="${HOME_DIR}/${FTP_UPLOAD_PATH}/영수증_김철수_A001.csv"
if [ ! -e "${SEED}" ]; then
	printf '\357\273\277ORDER_ID,USER_ID,NAME,ITEM_ID,ITEM_NAME,PRICE,RECEIVED_AT\r\nA001,USER01,김철수,ITEM01,선글라스,95000,2026-10-30 09:00:00\r\n' > "${SEED}"
	chown "${FTP_USER}:${FTP_USER}" "${SEED}"
	chmod 0444 "${SEED}"
fi

touch /var/log/vsftpd.log
tail -F /var/log/vsftpd.log &

exec vsftpd -opasv_address="${PASV_ADDRESS}" /etc/vsftpd/vsftpd.conf
