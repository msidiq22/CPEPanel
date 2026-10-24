package com.cpepanel.cpe

import org.json.JSONObject

/**
 * 一个 ubus 方法描述，data-driven 地驱动采集与展示。
 *
 * [params] 是调用参数。注意设备后端对部分接口【强制要求】参数，
 * 例如 zwrt_data 系列必须带 source_module + cid，否则直接返回 ubus status 2
 * （INVALID_ARGUMENT），而不是权限错误 —— 这一点在实测中踩过坑。
 *
 * [tier] 是刷新分层，用来控制那台很弱的小 httpd 的负载：
 *   0 = 每个采样周期都打（变化快、体量小）
 *   1 = 每 [Api] 调用方的 T1 周期（约 12 秒）
 *   2 = 每 T2 周期（约 45 秒）——这类数据基本不变（温控、APN、VPN、短信容量…）
 */
data class UbusMethod(
    val obj: String,
    val name: String,
    val label: String,
    val requiresLogin: Boolean = false,
    val group: String = "Other",
    val params: Map<String, Any?> = emptyMap(),
    val tier: Int = 1
) {
    val key: String get() = "$obj.$name"

    fun args(): JSONObject = JSONObject().also { o ->
        params.forEach { (k, v) -> if (v != null) o.put(k, v) }
    }
}

/**
 * 接口清单。全部来自对网页版前端 service_rpc.js 的解析 + 真机逐条实测，
 * 只收录【实测 status=0】的只读接口；前端定义了但设备 ACL 不给的
 * （network.lan / dhcp.lan / zwrt_router.alg / wireless.* 等）一律不收录。
 */
object Api {

    /**
     * zwrt_data 系列接口的公共参数。
     * source_module 的值就是前端 config.js 里的 RPC_MODULE_NAME，实测为 "web"。
     */
    private val CID1: Map<String, Any?> = mapOf("source_module" to "web", "cid" to 1)

    // ─────────────────────────── 免登录可读 ───────────────────────────

    /** 核心健康探针：免登录可读，字段最丰富 */
    val NET_INFO = UbusMethod("zte_nwinfo_api", "nwinfo_get_netinfo", "5G network info", group = "Network", tier = 0)
    val WAN_STATUS = UbusMethod("zwrt_router.api", "router_get_status_no_auth", "WAN status", group = "Network", tier = 0)
    val WLAN_REPORT = UbusMethod("zwrt_wlan", "report", "Wi-Fi overview", group = "Wi-Fi", tier = 1)
    val SIM_BRIEF = UbusMethod("zwrt_zte_mdm.api", "get_sim_info_before", "SIM status", group = "SIM", tier = 1)
    val LOGIN_INFO = UbusMethod("zwrt_web", "web_login_info", "Sign-in state", group = "System", tier = 1)
    val DEVICE_INFO = UbusMethod("zwrt_mc.device.manager", "get_device_info", "Device runtime info", group = "Device", tier = 2)
    val NFC_INFO = UbusMethod("zwrt_nfc", "zwrt_nfc_wifi_get", "NFC tap-to-connect", group = "System", tier = 2)

    val FREE = listOf(NET_INFO, WAN_STATUS, WLAN_REPORT, SIM_BRIEF, LOGIN_INFO, DEVICE_INFO, NFC_INFO)

    // ─────────────────────────── 网络 / WAN ───────────────────────────

    val ROUTER_STATUS = UbusMethod("zwrt_router.api", "router_get_status", "Router status", true, "Network", tier = 1)
    val WAN_IFACE = UbusMethod("zwrt_data", "get_wwaniface", "WAN interface details", true, "Network", CID1, tier = 1)
    /** 拨号模式详细参数：PPPoE / 静态 / 桥接 / IP 穿透 */
    val WAN_MODE_PARA = UbusMethod("zwrt_router.api", "router_get_wan_mode_para", "Dial mode parameters", true, "Network", tier = 2)

    /** 实时速率与本次连接累计。type=1 返回 real_tx_speed / real_rx_speed / real_time */
    val TRAFFIC_RT = UbusMethod("zwrt_data", "get_wwandst", "Live throughput", true, "Network", CID1 + mapOf("type" to 1), tier = 0)
    /** 本月累计流量。实测必须带 type=2 */
    val TRAFFIC_MONTH = UbusMethod("zwrt_data", "get_wwandst", "Monthly data", true, "Network", CID1 + mapOf("type" to 2), tier = 1)
    /** 套餐月限额（enable / type / value / ratio） */
    val TRAFFIC_LIMIT = UbusMethod("zwrt_data", "get_wwandst_monthlimit", "Data plan cap", true, "Network", CID1, tier = 2)
    /** 流量统计清零日（每月几号归零） */
    val TRAFFIC_CLEARDAY = UbusMethod("zwrt_data", "get_wwandst_clearday", "Data reset day", true, "Network", CID1, tier = 2)

    /** 在线终端数量按接入方式细分 */
    val USER_NUM = UbusMethod("zwrt_router.api", "router_get_user_list_num", "Client counts", true, "Network", tier = 0)
    /** 在线终端明细：主机名 / IP / MAC / 接入方式 / 在线时长 */
    val LAN_ACCESS = UbusMethod("zwrt_router.api", "router_lan_access_list", "Online clients", true, "Network", tier = 1)
    val ARP_TABLE = UbusMethod("zwrt_router.api", "router_get_arptable", "ARP table", true, "Network", tier = 2)
    /** 用户自定义的终端名称（改过主机的设备） */
    val LAN_HOSTNAME = UbusMethod("zwrt_router.api", "router_get_modified_lan_hostname", "Custom hostnames", true, "Network", tier = 2)
    /** MAC 访问控制白/黑名单里已设置的条目 */
    val MAC_PCTRL = UbusMethod("zwrt_router.api", "router_get_macs_setted_pctrl", "MAC access control", true, "Network", tier = 2)
    /** 二次认证（上网认证）成功 / 失败列表 */
    val SECOND_AUTH_SUCC = UbusMethod("zwrt_redirect.api", "get_second_auth_succ_list", "Web auth passed", true, "Network", tier = 2)
    val SECOND_AUTH_FAIL = UbusMethod("zwrt_redirect.api", "get_second_auth_fail_list", "Web auth pending", true, "Network", tier = 2)
    /** DDNS 动态域名配置与注册状态 */
    val DDNS = UbusMethod("zwrt_router.api", "router_get_ddns", "DDNS", true, "Network", tier = 2)
    /** VPN 隧道（pptp/l2tp/gre/ipsec/vxlan/openvpn/wireguard）配置与连接状态 */
    val TUNNEL = UbusMethod("zwrt_tunnel.config", "get", "VPN tunnels", true, "Network", tier = 2)

    // ─────────────────────────── APN ───────────────────────────

    val APN_MODE = UbusMethod("zwrt_apn_object", "get_apn_mode", "APN mode", true, "APN", tier = 2)
    val APN_AUTO = UbusMethod("zwrt_apn_object", "getAutoApnList", "Auto APN list", true, "APN", tier = 2)
    val APN_MANU = UbusMethod("zwrt_apn_object", "getManuApnList", "Manual APN list", true, "APN", tier = 2)
    val APN_AT_CID = UbusMethod("zwrt_apn_object", "getApnAtCid", "Current APN details", true, "APN", tier = 2)

    // ─────────────────────────── Wi-Fi ───────────────────────────

    /** 各 SSID 接口详情：section_name / ssid / encryption / hidden / disabled / key_encode */
    val WLAN_IFACE = UbusMethod("zwrt_wlan", "iface_report", "Wi-Fi interface details", true, "Wi-Fi", tier = 1)
    /**
     * Wi-Fi 密钥密文。返回的 key_encode 用【会话密钥】做 AES-256-CBC 加密，
     * 必须先完成 web_crt_get + web_http_enstr_set 协商才能解开。
     */
    val WLAN_KEY = UbusMethod("zwrt_wlan", "wlan_get_key", "Wi-Fi keys", true, "Wi-Fi", tier = 1)
    val WLAN_AGENTS = UbusMethod("zwrt_wlan", "get_agent_list", "Wi-Fi client list", true, "Wi-Fi", tier = 1)
    /** 历史上连过的无线终端 */
    val WLAN_AGENT_HISTORY = UbusMethod("zwrt_wlan", "get_agent_history", "Wi-Fi client history", true, "Wi-Fi", tier = 2)
    /** 各 SSID 的 WPS 配对状态 */
    val WLAN_WPS = UbusMethod("zwrt_wlan", "get_wps_status", "WPS pairing state", true, "Wi-Fi", tier = 2)
    /** 访客网络剩余可用时长 */
    val WLAN_GUEST_TIME = UbusMethod("zwrt_wlan", "wlan_get_guest_access_left_time", "Guest network time left", true, "Wi-Fi", tier = 2)
    /** Wi-Fi 隔离相关开关（主/访客互访、AP 隔离、隐私隔离…） */
    val WLAN_ISOLATE = UbusMethod("zwrt_router.api", "router_get_wifi_isolate", "Wi-Fi isolation", true, "Wi-Fi", tier = 2)
    /** 周边 Wi-Fi 扫描结果（需先在网页触发扫描） */
    val WLAN_SCAN = UbusMethod("zwrt_wlan", "get_scan_results", "Nearby Wi-Fi scan", true, "Wi-Fi", tier = 2)
    /** 蹭网检测状态 */
    val WLAN_MOVING = UbusMethod("zwrt_wlan", "get_wifi_moving_info", "Rogue AP detection", true, "Wi-Fi", tier = 2)

    // ─────────────────────────── 信号 / 小区 ───────────────────────────

    val NR_NEIGHBOR = UbusMethod("zte_nwinfo_api", "nwinfo_get_nr5g_nbr_contents", "5G neighbour cells", true, "Signal", tier = 2)
    val LTE_NEIGHBOR = UbusMethod("zte_nwinfo_api", "nwinfo_get_lte_nbr_contents", "4G neighbour cells", true, "Signal", tier = 2)
    /** 信号质量检测进度（网页端"Signal quality check"用） */
    val SNR_QUALITY = UbusMethod("zte_nwinfo_api", "nwinfo_get_progress_and_quality", "Signal quality check", true, "Signal", tier = 2)

    // ─────────────────────────── SIM ───────────────────────────

    val SIM_DETAIL = UbusMethod("zwrt_zte_mdm.api", "get_sim_info", "SIM details", true, "SIM", tier = 1)
    /** SIM 锁剩余可尝试次数 */
    val SIMLOCK_TRIALS = UbusMethod("zwrt_zte_mdm.api", "get_simlock_available_trials", "SIM lock attempts left", true, "SIM", tier = 2)
    /**
     * 本机固件下 `sim_get_slot` 存在但返回空（实测 status=0 且无数据），
     * 当前卡槽只能从 [SIM_BRIEF] / [SIM_DETAIL] 的 `current_sim_slot` 读取。
     * 仍然保留调用，用于在别的固件上探测卡槽列表。
     */
    val SIM_SLOT = UbusMethod("zwrt_zte_mdm.api", "sim_get_slot", "SIM slot list", true, "SIM", tier = 2)

    // ─────────────────────────── 短信 ───────────────────────────

    /**
     * 短信列表。
     *
     * 三条实测得出的硬约束，缺一不可：
     *  1. `mem_store = 1` 才返回设备内存储的短信；传 0（SIM 卡）在本机恒返回空数组；
     *  2. `tags` 与 `order_by` 是【必填】，省略直接 ubus status 2 (INVALID_ARGUMENT)；
     *  3. 单次可返回上百条，正文是 UTF-16BE 十六进制，响应体可达数十 KB。
     *
     * 正因为第 3 点，本接口【绝不进入轮询分层】——只在用户打开短信页时单独拉一次，
     * 否则那台小 httpd 会被打挂。
     */
    val SMS_LIST = UbusMethod(
        "zwrt_wms", "zte_libwms_get_sms_data", "SMS list", true, "SMS",
        mapOf(
            "page" to 0,
            "data_per_page" to 500,
            "mem_store" to 1,
            "tags" to 10,
            "order_by" to "order by id desc"
        ),
        tier = 2
    )
    /** 短信服务初始化状态与收发标志 */
    val SMS_NVITEMS = UbusMethod("zwrt_wms", "zwrt_get_wms_nvitems", "SMS service state", true, "SMS", tier = 2)
    /** 状态报告（送达回执）列表 */
    val SMS_STATUS_RPT = UbusMethod(
        "zwrt_wms", "zwrt_wms_get_status_rpt_data", "SMS delivery reports", true, "SMS",
        mapOf("page" to 0, "data_per_page" to 50), tier = 2
    )

    // ─────────────────────────── 设备 / 系统 ───────────────────────────

    val SYS_TIME = UbusMethod("zwrt_sntp", "get_systime", "Device time", true, "System", tier = 1)
    /** 时间同步方式：NITZ / SNTP / 手动 */
    val SNTP_MODE = UbusMethod("zwrt_sntp", "get_systime_mode", "Time sync mode", true, "System", tier = 2)
    val SNTP_SYNC = UbusMethod("zwrt_sntp", "get_sync_state", "Time sync state", true, "System", tier = 2)
    /** 温控策略档位 */
    val THERMAL = UbusMethod("zwrt_bsp.thermal", "get_policy", "Thermal policy", true, "Device", tier = 2)
    /** LED 夜间模式开关与时间段 */
    val NIGHT_MODE = UbusMethod("zwrt_led", "get_nightmode_state", "Night mode", true, "Device", tier = 2)
    /** Mesh 组网下各节点的 LED 夜间设置 */
    val LED_MESH = UbusMethod("zwrt_led", "get_led_mesh_settings", "Mesh LED settings", true, "Device", tier = 2)
    /** 省电 / 休眠参数 */
    val SLEEP_PARA = UbusMethod("zwrt_sleep.parameter", "get", "Power saving & sleep", true, "Device", tier = 2)
    /** 短信容量与未读数量 */
    val SMS_CAPACITY = UbusMethod("zwrt_wms", "zwrt_wms_get_wms_capacity", "SMS capacity", true, "System", tier = 2)
    /** 短信中心号码与状态报告设置 */
    val SMS_PARAM = UbusMethod("zwrt_wms", "zte_wms_get_parameter", "SMS parameters", true, "System", tier = 2)
    val NEW_VERSION = UbusMethod("zwrt_zte_dm", "check_new_version", "Firmware update check", true, "System", tier = 2)
    /** 会话与机型信息（登录用户、剩余超时、登录失败次数） */
    val WEB_INFO = UbusMethod("zwrt_web", "web_info", "Session & model info", true, "System", tier = 1)

    // ─────────────────────────── 分组 ───────────────────────────

    val AUTH_NET = listOf(
        ROUTER_STATUS, WAN_IFACE, WAN_MODE_PARA,
        TRAFFIC_RT, TRAFFIC_MONTH, TRAFFIC_LIMIT, TRAFFIC_CLEARDAY,
        USER_NUM, LAN_ACCESS, ARP_TABLE, LAN_HOSTNAME, MAC_PCTRL,
        SECOND_AUTH_SUCC, SECOND_AUTH_FAIL, DDNS, TUNNEL
    )
    val AUTH_WIFI = listOf(
        WLAN_IFACE, WLAN_KEY, WLAN_AGENTS, WLAN_AGENT_HISTORY,
        WLAN_WPS, WLAN_GUEST_TIME, WLAN_ISOLATE, WLAN_SCAN, WLAN_MOVING
    )
    val AUTH_APN = listOf(APN_MODE, APN_AUTO, APN_MANU, APN_AT_CID)
    val AUTH_SIGNAL = listOf(NR_NEIGHBOR, LTE_NEIGHBOR, SNR_QUALITY)
    val AUTH_SIM = listOf(SIM_DETAIL, SIMLOCK_TRIALS, SIM_SLOT)
    val AUTH_DEV = listOf(
        SYS_TIME, SNTP_MODE, SNTP_SYNC, THERMAL, NIGHT_MODE, LED_MESH, SLEEP_PARA,
        SMS_CAPACITY, SMS_PARAM, NEW_VERSION, WEB_INFO
    )

    /**
     * 需要登录后才能读的接口全集。逐条容错：
     * 不支持的机型会显示具体原因，不影响其它卡片。
     * 顺序无关紧要 —— UI 一律按具名常量引用，不要用下标。
     */
    val AUTH = AUTH_NET + AUTH_WIFI + AUTH_APN + AUTH_SIGNAL + AUTH_SIM + AUTH_DEV

    val ALL: List<UbusMethod> get() = FREE + AUTH

    /**
     * 【写操作】接口。
     *
     * 与上面的只读接口严格分开管理，原因很直接：这些调用会真实改变设备状态、
     * 或者真的从 SIM 卡发出一条短信到运营商网络。它们只能由用户在界面上
     * 明确点击后触发一次，**绝不进入任何自动轮询**。
     *
     * 另外注意：本 App 的写操作一律「一步一确认」，发短信、切卡槽前都会弹确认框，
     * 不做任何静默重试 —— 避免重试导致重复发送。
     */
    object Write {
        /**
         * 切换 SIM 卡槽。参数 `{"slot": 1|2}`。
         * 真机实测：受理时响应体是**空的**（没有 result 字段），只有参数非法才抛 status 2，
         * 所以判定只能用「没抛异常 = 受理」，真实结果要轮询
         * `get_sim_info_before.current_sim_slot` 确认 —— 见 [CpeViewModel.switchSimSlot]。
         * 切换过程中 `get_sim_info_before.switch_card_status` 会变化，可用于显示进度。
         */
        val SIM_SWITCH = UbusMethod("zwrt_zte_mdm.api", "sim_switch_slot", "Switch SIM slot", true, "SIM")

        /**
         * 发送短信。
         * 参数：number / sms_time / message_body（UTF-16BE 十六进制）/ id / encode_type，
         * 具体构造见 [SmsCodec]。返回体 `result`：3 = 受理，2 = 被拒（号码为空/非法）。
         * 之后还要轮询 [SMS_CMD_STATUS]（sms_cmd=4）确认真正发出。
         */
        val SMS_SEND = UbusMethod("zwrt_wms", "zte_libwms_send_sms", "Send SMS", true, "SMS")

        /** 删除短信。参数 `{"id": "1;2;3;"}` —— 注意末尾必须带分号，这是前端原样拼的格式 */
        val SMS_DELETE = UbusMethod("zwrt_wms", "zwrt_wms_delete_sms", "Delete SMS", true, "SMS")

        /** 保存草稿 */
        val SMS_DRAFT = UbusMethod("zwrt_wms", "zwrt_wms_write_sms", "Save draft", true, "SMS")

        /**
         * 写操作的进度查询。参数 `{"sms_cmd": N}`：
         *   1 = 短信服务就绪检测
         *   4 = 发送   5 = 存草稿   6 = 删除
         * 返回 `sms_cmd_status_result`：3 = 成功，2 = 失败，其它 = 进行中（需继续轮询）。
         */
        val SMS_CMD_STATUS = UbusMethod("zwrt_wms", "zwrt_wms_get_cmd_status", "SMS command state", true, "SMS")

        val ALL: List<UbusMethod> = listOf(SIM_SWITCH, SMS_SEND, SMS_DELETE, SMS_DRAFT, SMS_CMD_STATUS)
    }

    /** 本 App 实际会调用的全部接口（含按需拉取的短信与写操作），仅用于统计展示 */
    val EVERYTHING: List<UbusMethod> get() = ALL + SMS_LIST + SMS_NVITEMS + SMS_STATUS_RPT
}
