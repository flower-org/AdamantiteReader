# Adamant Protocol Specification (USB Sniffer)

## Overview

- **Byte order:** Big-Endian throughout
- **IP protocol numbers:** IANA standard (copied directly from packet headers)
- **String encoding:** UTF-8

---

## Message Types

| Value  | Name        | Description                                                      |
|--------|-------------|------------------------------------------------------------------|
| `0x01` | `PACKET`    | Ethernet frame event (full frame or headers-only)                |
| `0x02` | `DROP_INFO` | Sniffer dropped packets; indicates a gap in the capture stream   |
| `0x03` | `INFO`      | General informational/log message (UTF-8 text)                   |
| `0x04` | `COMMAND`   | Service/control message sent from PC to router                   |
| `0x05` | `ACK`       | Router response to a `COMMAND`                                   |

---

## Common Message Envelope

Every message begins with this fixed 12-byte header:

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                         MAGIC (0x7E4C9B2F)                    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|    VERSION    |     TYPE      |       SEQUENCE NUMBER (u16)   |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       PAYLOAD LENGTH (u32)                    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

| Field         | Type | Description                                                       |
|---------------|------|-------------------------------------------------------------------|
| `MAGIC`       | u32  | Always `0x7E4C9B2F` — used for stream synchronisation             |
| `VERSION`     | u8   | Protocol version; currently `0x01`                                |
| `TYPE`        | u8   | Message type (see table above)                                    |
| `SEQUENCE`    | u16  | Monotonic counter, wraps at 65535; Java uses this to detect gaps  |
| `PAYLOAD_LEN` | u32  | Length in bytes of the payload that follows this header           |

---

## PACKET Message Payload (`TYPE = 0x01`)

A `PACKET` message always contains either a full Ethernet frame or a trimmed copy of
that frame truncated at the end of the last protocol header (Ethernet + IP + TCP/UDP),
with the payload stripped — never both, never neither. The `FULL_FRAME` flag selects
the mode. In both cases the data is a raw `memcpy` from the start of the frame; the
only difference is how many bytes are copied.

### Layout

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|    FLAGS      |  FROM_IFACE   |   TO_IFACE    |   RESERVED    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       RX_QUEUE_SIZE (u16)     |      TX_QUEUE_SIZE (u16)      |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|    USB_SNIFF_QUEUE_SIZE (u16) |         FRAME_LENGTH (u32)... |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  ...FRAME_LENGTH (u32)        |   HDR_LENGTH  |   RESERVED    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
|   Raw Ethernet frame  (if FULL_FRAME = 1, FRAME_LENGTH bytes) |
|        OR                                                     |
|   Frame truncated after last protocol header                  |
|                       (if FULL_FRAME = 0, HDR_LENGTH bytes)   |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### Field Descriptions

| Field                  | Type     | Description                                                                                                                               |
|------------------------|----------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `FLAGS`                | u8       | Bitmask — see FLAGS table below                                                                                                           |
| `FROM_IFACE`           | u8       | Ingress interface index                                                                                                                   |
| `TO_IFACE`             | u8       | Egress interface index                                                                                                                    |
| `RESERVED`             | u8       | Set to `0x00`                                                                                                                             |
| `RX_QUEUE_SIZE`        | u16      | RX queue depth at time of capture                                                                                                         |
| `TX_QUEUE_SIZE`        | u16      | TX queue depth at time of capture                                                                                                         |
| `USB_SNIFF_QUEUE_SIZE` | u16      | USB sniffer TX queue depth at time of capture                                                                                             |
| `FRAME_LENGTH`         | u32      | Length of the original Ethernet frame in bytes (always present)                                                                           |
| `HDR_LENGTH`           | u8       | Length of the truncated frame in bytes; only meaningful when `FULL_FRAME = 0`                                                             |
| `RESERVED`             | u8       | Set to `0x00`                                                                                                                             |
| Data                   | variable | Full Ethernet frame (`FRAME_LENGTH` bytes) if `FULL_FRAME = 1`, otherwise frame truncated after last protocol header (`HDR_LENGTH` bytes) |

### FLAGS Byte

| Bit  | Name         | Meaning                                                            |
|------|--------------|--------------------------------------------------------------------|
| 7    | `FULL_FRAME` | `1` = full Ethernet frame follows; `0` = truncated frame follows   |
| 6    | `IS_DROPPED` | This packet was dropped by the router                              |
| 5–0  | —            | Reserved; set to `0`                                               |

### Capture Modes

| `FULL_FRAME` | Mode        | Data appended                                                              | Java behaviour                                 |
|:------------:|-------------|----------------------------------------------------------------------------|------------------------------------------------|
| `1`          | Full        | Entire Ethernet frame                                                      | Parse headers directly from frame              |
| `0`          | Lightweight | Frame truncated after last protocol header — payload stripped              | Parse headers from truncated frame; no payload |

The firmware uses a single `memcpy` in both modes — either the whole frame, or just
the bytes up to `HDR_LENGTH`. No per-field parsing is required on the router side.

---

## DROP_INFO Message Payload (`TYPE = 0x02`)

Sent by the sniffer when its USB TX queue overflows and one or more packets could not
be serialized to USB. This message itself represents a gap in the capture stream —
the dropped packets are gone and their contents are unknown.

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       DROP_COUNT (u32)                        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|    USB_SNIFF_QUEUE_SIZE (u16) |         RESERVED (u16)        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

| Field                  | Type | Description                                                            |
|------------------------|------|------------------------------------------------------------------------|
| `DROP_COUNT`           | u32  | Number of packets dropped by the sniffer TX since the last `DROP_INFO` |
| `USB_SNIFF_QUEUE_SIZE` | u16  | USB sniffer queue depth at time of reporting                           |
| `RESERVED`             | u16  | Set to `0x0000`                                                        |

---

## INFO Message Payload (`TYPE = 0x03`)

TBD

---

## COMMAND / ACK Messages (`TYPE = 0x04` / `0x05`)

TBD

---

## Appendix A: Lightweight Mode Header Length Reference

Minimum number of bytes that must be copied to guarantee the presence of all key
fields:
- Src/Dst MAC
- EtherType
- Frame length
- Src/Dst IP (4 or 6)
- Src/Dst port
- Protocol

Transport headers are truncated after the first 4 bytes (Src port + Dst port for
TCP/UDP) or 2 bytes (Type + Code for ICMP/ICMPv6). Everything after is payload and
is not copied in lightweight mode.

The max `HDR_LENGTH` for lightweight mode is **96 bytes**, which covers all stacks
below with alignment headroom.

| Stack | Calculation | HDR_LENGTH                                                 |
|-------|-------------|------------------------------------------------------------|
| Ethernet + IPv4 + ICMP | 14 + 20 + 2 | 36 bytes                                  |
| Ethernet + IPv4 + TCP | 14 + 20 + 4 | 38 bytes                                   |
| Ethernet + IPv4 + UDP | 14 + 20 + 4 | 38 bytes                                   |
| Ethernet + IPv4 + UDP + DNS | 14 + 20 + 4 | 38 bytes                             |
| Ethernet + IPv4 + UDP + DHCP | 14 + 20 + 4 | 38 bytes                            |
| Ethernet + ARP | 14 + 28 | 42 bytes                                              |
| Ethernet + IPv6 + ICMPv6 | 14 + 40 + 2 | 56 bytes                                |
| Ethernet + IPv6 + TCP | 14 + 40 + 4 | 58 bytes                                   |
| Ethernet + IPv6 + UDP | 14 + 40 + 4 | 58 bytes                                   |
| Ethernet + IPv6 + UDP + DNS | 14 + 40 + 4 | 58 bytes                             |
| Ethernet + IPv6 + UDP + DHCP | 14 + 40 + 4 | 58 bytes                            |
| Ethernet + IPv4 + UDP + QUIC short | 14 + 20 + 4 + 21 | 59 bytes                 |
| Ethernet + IPv4 + UDP + QUIC long | 14 + 20 + 4 + 26 | 64 bytes                  |
| Ethernet + IPv6 + UDP + QUIC short | 14 + 40 + 4 + 21 | 79 bytes                 |
| Ethernet + IPv6 + UDP + QUIC long | 14 + 40 + 4 + 26 | **84 bytes** ← worst case |

