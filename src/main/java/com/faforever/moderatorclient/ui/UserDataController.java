package com.faforever.moderatorclient.ui;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
public class UserDataController {
    private UserInfo userInfo = new UserInfo();
    private AccountHistory accountHistory = new AccountHistory();
    private HardwareInfo hardwareInfo = new HardwareInfo();

    @Getter
    @Setter
    @ToString
    public static class UserInfo {
        private String addedOn;
        private String lastEdit;
        private String userId;
        private String userName;
        private String comment;
        private String reason;
        private String linkedAccount;

        private List<EmailEntry> email = new ArrayList<>();
        private List<UserAgentEntry> userAgent = new ArrayList<>();
        private List<BanInfo> bans = new ArrayList<>();
    }

    @Getter
    @Setter
    @ToString
    public static class AccountHistory {
        private List<HistoryEntry> history = new ArrayList<>();
        private List<LoginEntry> lastLogins = new ArrayList<>();
        private List<String> previousNames = new ArrayList<>();
        private List<String> registrationDate = new ArrayList<>();
    }

    @Getter
    @Setter
    @ToString
    public static class HardwareInfo {
        private List<IpAddressEntry> ipAddresses = new ArrayList<>();
        private List<UuidEntry> uuidEntries = new ArrayList<>();
        private List<DeviceIdEntry> deviceIdEntries = new ArrayList<>();
        private List<SerialNumberEntry> serialNumberEntries = new ArrayList<>();
        private List<ProcessorIdEntry> processorIdEntries = new ArrayList<>();
        private List<CpuNameEntry> cpuNameEntries = new ArrayList<>();
        private List<BiosVersionEntry> biosVersionEntries = new ArrayList<>();
        private List<ManufacturerEntry> manufacturerEntries = new ArrayList<>();
        private List<HashEntry> hashEntries = new ArrayList<>();
        private List<MemorySerialNumberEntry> memorySerialNumberEntries = new ArrayList<>();
        private List<VolumeSerialNumberEntry> volumeSerialNumberEntries = new ArrayList<>();
    }

    @Getter
    @Setter
    @ToString
    public static class BanInfo {
        private String banId;
        private String banStatus;
        private String banExpiresAt;
        private String banDuration;
        private String banCreatedAt;
        private String banReason;
        private String banAuthor;
        private String banRevocationReason;
        private String banRevocationAuthor;
        private String banRevocationAt;
    }

    // --- Typed classes for UserInfo ---
    @Getter
    @Setter
    @ToString
    public static class EmailEntry {
        private String email;
        private String addedOn;
    }

    @Getter
    @Setter
    @ToString
    public static class UserAgentEntry {
        private String userAgent;
        private String addedOn;
    }

    // --- Typed classes for AccountHistory ---
    @Getter
    @Setter
    @ToString
    public static class HistoryEntry {
        private String action;
        private String timestamp;
        private String description;
    }

    @Getter
    @Setter
    @ToString
    public static class LoginEntry {
        private String ip;
        private String addedOn;
    }

    // --- Typed classes for HardwareInfo ---
    @Getter
    @Setter
    @ToString
    public static class IpAddressEntry {
        private String ip;
        private String addedOn;
    }

    @Getter
    @Setter
    @ToString
    public static class UuidEntry {
        private String uuid;
        private String addedOn;
    }

    @Getter
    @Setter
    @ToString
    public static class DeviceIdEntry {
        private String deviceId;
        private String addedOn;
    }

    @Getter
    @Setter
    @ToString
    public static class SerialNumberEntry {
        private String serialNumber;
        private String addedOn;
    }

    @Getter
    @Setter
    @ToString
    public static class ProcessorIdEntry {
        private String processorId;
        private String addedOn;
    }

    @Getter
    @Setter
    @ToString
    public static class CpuNameEntry {
        private String cpuName;
        private String addedOn;
    }

    @Getter
    @Setter
    @ToString
    public static class BiosVersionEntry {
        private String biosVersion;
        private String addedOn;
    }

    @Getter
    @Setter
    @ToString
    public static class ManufacturerEntry {
        private String manufacturer;
        private String addedOn;
    }

    @Getter
    @Setter
    @ToString
    public static class HashEntry {
        private String hash;
        private String addedOn;
    }

    @Getter
    @Setter
    @ToString
    public static class MemorySerialNumberEntry {
        private String memorySerialNumber;
        private String addedOn;
    }

    @Getter
    @Setter
    @ToString
    public static class VolumeSerialNumberEntry {
        private String volumeSerialNumber;
        private String addedOn;
    }
}
