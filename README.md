# wgsl-fuzz
Technology for fuzzing tooling for the WebGPU shading language



## Server deployment

To deploy server (adapt passwords and SERVERNAME):

```
openssl req -x509 -newkey rsa:4096 -keyout key.pem -out cert.pem -days 365 -nodes -subj "/CN=SERVERNAME"

openssl pkcs12 -export -in cert.pem -inkey key.pem -out keystore.p12 -name alias

keytool -importkeystore -deststorepass your_password -destkeypass your_password -destkeystore keystore.jks -srckeystore keystore.p12 -srcstoretype PKCS12 -srcstorepass your_password -alias alias
```

As root or via sudo:

```
setcap 'cap_net_bind_service=+ep' $(readlink -f $(which java))
```

