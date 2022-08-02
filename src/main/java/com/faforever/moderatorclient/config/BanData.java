package com.faforever.moderatorclient.config;

import java.util.Arrays;
import java.util.List;

public class BanData {
    List<String> blacklist_permanent_vsn = Arrays.asList(
            "9617F53D", "B46B373F", "5817581F", "CE737DA9", "3E06A8E4", "16C603C4", "D278131F", "DD6E5A46",
            "3E6ADA75", "3E35B576", "7EB154C6", "A63BD8E5", "4E9190BF", "14BCB912", "4CC68FE6", "7A854A56",
            "94742677", "383111BC", "59684F69", "50935221", "ECC1F7CA", "A490621D", "74707406", "DDDDDDDD",
            "DD6E5555", "50935238", "4C42DD89", "0C0C0542", "0AAF500E", "6AD134F4", "CC6D51DD", "E6F31E6C",
            "94B67A82", "183AAE7F", "E23919A5", "F8E5058A", "00AD7C06", "A2FBE5FD", "D6FE2B67", "88D7A2DB",
            "C224A444", "BE569140", "C0F66575", "5E5B29A7", "88D7A2DB", "FE26232E", "6E0AB59D", "F8EC8982",
            "2673C0E9", "BAF7732E", "907826CC", "F8E5058A", "F8E5058A", "D05841A8", "426583F6", "8C9A8E2A",
            "A0FED266", "D0613863", "C6DA4E26", "D49ABF3C", "66941B26", "3467FEB8", "688C8DB9", "C431FB03",
            "5AAA7235", "88D7A2DB", "45AFCEFF", "CC5C10CC", "7E9D75AB", "35A61764", "DC2CF46B", "B24052A9",
            "A0FED266", "D0613863", "C6DA4E26", "D49ABF3C", "66941B26", "3467FEB8", "688C8DB9", "C431FB03",
            "5AAA7235", "8C9A8E2A", "92A0ACCA", "D0B188E9", "EC83C706", "8A8EA38E", "900F5EFF", "2C2678AE"
            );
    public List<String> getBlacklistPermanentVSN() {
        return blacklist_permanent_vsn;
    }

    List<String> blacklist_temporary_VSN = Arrays.asList(
            "BCCA3D21", // 221130 //yymmdd
            "3AD22814", // 220626
            "A88F6DBA", // 220909
            "2A4BB601", // 220909
            "14B75FD6", // 221202
            "D26F8AFF", // 221202
            "5CA457C4", // 221202
            "56E1CB77", // 221202
            "2A46E321", // 221202
            "1CBCAEC1" // 220623

    );
    public List<String> getBlacklistTemporaryVSN() {
        return blacklist_temporary_VSN;
    }
    List<String> blacklist_permanent_ip = Arrays.asList(
            "82.28.138.162", "5.166.220.47", "5.164.240.15", "217.107.124.99", "46.17.251.250", "94.143.50.159",
            "71.90.105.78", "71.82.168.29", "213.114.230.165", "37.112.20.184", "2.92.246.179", "71.82.168.29",
            "94.41.86.117"
    );
    public List<String> getBlacklistPermanentIP() { return blacklist_permanent_ip; }
}
