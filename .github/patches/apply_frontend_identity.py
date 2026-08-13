from pathlib import Path

proxy = Path('src/main/java/net/raphimc/viaproxy/proxy/session/ProxyConnection.java')
text = proxy.read_text()
replacements = [
    ('import java.util.List;\n', 'import java.util.List;\nimport java.util.UUID;\n'),
    ('    private HostAndPort clientHandshakeAddress;\n    private GameProfile gameProfile;\n', '    private HostAndPort clientHandshakeAddress;\n    private UUID frontendProfileId;\n    private GameProfile gameProfile;\n'),
    ('    public GameProfile getGameProfile() {\n', '    public UUID getFrontendProfileId() {\n        return this.frontendProfileId;\n    }\n\n    public void setFrontendProfileId(final UUID frontendProfileId) {\n        this.frontendProfileId = frontendProfileId;\n    }\n\n    public GameProfile getGameProfile() {\n'),
]
for old, new in replacements:
    if text.count(old) != 1:
        raise SystemExit(f'ProxyConnection context mismatch: {old!r}')
    text = text.replace(old, new, 1)
proxy.write_text(text)

login = Path('src/main/java/net/raphimc/viaproxy/proxy/packethandler/LoginPacketHandler.java')
text = login.read_text()
old = '            proxyConnection.setLoginHelloPacket(loginHelloPacket);\n            if (loginHelloPacket.uuid != null) {\n'
new = '            proxyConnection.setLoginHelloPacket(loginHelloPacket);\n            proxyConnection.setFrontendProfileId(\n                    loginHelloPacket.uuid != null\n                            ? loginHelloPacket.uuid\n                            : GameProfileUtil.getOfflinePlayerUuid(loginHelloPacket.name)\n            );\n            if (loginHelloPacket.uuid != null) {\n'
if text.count(old) != 1:
    raise SystemExit('LoginPacketHandler context mismatch')
login.write_text(text.replace(old, new, 1))
