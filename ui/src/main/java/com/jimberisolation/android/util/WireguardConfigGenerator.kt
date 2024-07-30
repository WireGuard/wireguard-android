data class Company(val name: String)
data class Daemon(val privateKey: String, val ipAddress: String)
data class DnsServer(val ipAddress: String)
data class NetworkController(val routerPublicKey: String, val endpointAddress: String, val port: Int)

fun GenerateWireguardConfig(company: Company, daemon: Daemon, dnsServer: DnsServer, networkController: NetworkController): String {
    return """
        [Interface]
        #company ${company.name}
        PrivateKey = ${daemon.privateKey}
        Address = ${daemon.ipAddress}
        DNS = ${dnsServer.ipAddress}
        
        [Peer]
        PublicKey = ${networkController.routerPublicKey}
        AllowedIPs = 0.0.0.0/0
        Endpoint = ${networkController.endpointAddress}:${networkController.port}
        PersistentKeepalive = 15
    """.trimIndent()
}