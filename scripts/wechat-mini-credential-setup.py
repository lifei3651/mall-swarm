#!/usr/bin/env python3
"""Server-only credential check/install; never print credentials or API tokens."""
import json
import os
import re
import sys
import urllib.error
import urllib.request


def main():
    if len(sys.argv) != 3 or sys.argv[1] not in ('check', 'install'):
        raise ValueError('invalid invocation')
    mode, path = sys.argv[1:]
    st = os.stat(path)
    if st.st_mode & 0o077 or st.st_uid != os.getuid():
        raise ValueError('private input permissions required')
    values = {}
    with open(path, encoding='utf-8') as stream:
        for line in stream:
            match = re.fullmatch(r'\s*(WECHAT_MINI_PROGRAM_[A-Z_]+)\s*=\s*(.*?)\s*', line)
            if match:
                value = match[2]
                if len(value) >= 2 and value[0] in ('"', "'") and value[-1] == value[0]:
                    value = value[1:-1]
                values[match[1]] = value
    appid = values.get('WECHAT_MINI_PROGRAM_APP_ID', '')
    secret = values.get('WECHAT_MINI_PROGRAM_APP_SECRET', '')
    if appid != 'wxd26e0a4e41df392b' or not re.fullmatch(r'[a-fA-F0-9]{32}', secret):
        raise ValueError('invalid local credential format or AppID')
    if mode == 'check':
        body = json.dumps(dict(grant_type='client_credential', appid=appid,
                               secret=secret, force_refresh=False)).encode()
        request = urllib.request.Request('https://api.weixin.qq.com/cgi-bin/stable_token',
                                         data=body, headers={'Content-Type': 'application/json'})
        # Do not follow redirects with the credential-bearing request.
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, req, fp, code, msg, headers, newurl):
                return None
        with urllib.request.build_opener(NoRedirect).open(request, timeout=20) as response:
            data = json.loads(response.read(65536))
        if data.get('errcode') or not data.get('access_token'):
            print('wechat_credential_check=failed error_code=' + str(int(data.get('errcode', -1))))
            return 3
        print('wechat_credential_check=passed')
        return 0
    if os.environ.get('LINGQIMALL_RELEASE_AUTHORIZATION') != '1.0.126':
        raise ValueError('release authorization required')
    target = '/etc/lingqimall/wechat-mini-program.env'
    fields = {
        'ENABLED': 'true', 'PHONEAUTHORIZATIONENABLED': 'true',
        'APPID': appid, 'APPSECRET': secret,
        'PRIVACYCONSENTVERSION': 'MINI_PROGRAM_PRIVACY_V1',
        'MINIPROGRAMSTATE': 'formal',
        'SUBSCRIBEMESSAGEENABLED': 'false', 'SHIPPINGINFOENABLED': 'false'
    }
    content = ''.join('SHOP_WECHATMINIPROGRAM_' + key + '=' + value + '\n' for key, value in fields.items())
    fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as stream:
        stream.write(content)
        stream.flush()
        os.fsync(stream.fileno())
    print('wechat_private_configuration=installed')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as error:
        # Exception text can include HTTP details; emit only the class name.
        print('wechat_credential_setup=failed type=' + type(error).__name__)
        sys.exit(2)
