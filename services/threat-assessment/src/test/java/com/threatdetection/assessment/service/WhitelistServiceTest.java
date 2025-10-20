package com.threatdetection.assessment.service;

import com.threatdetection.assessment.model.WhitelistConfig;
import com.threatdetection.assessment.repository.WhitelistConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 白名单服务单元测试
 * 
 * @author Security Team
 * @version 2.0
 * @since Phase 5
 */
@ExtendWith(MockitoExtension.class)
class WhitelistServiceTest {
    
    @Mock
    private WhitelistConfigRepository repository;
    
    @InjectMocks
    private WhitelistService whitelistService;
    
    private WhitelistConfig ipWhitelist;
    private WhitelistConfig macWhitelist;
    private WhitelistConfig combinedWhitelist;
    private WhitelistConfig expiredWhitelist;
    
    @BeforeEach
    void setUp() {
        // IP白名单
        ipWhitelist = WhitelistConfig.builder()
            .id(1L)
            .customerId("customer-001")
            .whitelistType("IP")
            .ipAddress("192.168.1.10")
            .reason("管理员工作站")
            .isActive(true)
            .createdAt(Instant.now())
            .build();
        
        // MAC白名单
        macWhitelist = WhitelistConfig.builder()
            .id(2L)
            .customerId("customer-001")
            .whitelistType("MAC")
            .macAddress("00:11:22:33:44:55")
            .reason("管理员笔记本")
            .isActive(true)
            .createdAt(Instant.now())
            .build();
        
        // 组合白名单
        combinedWhitelist = WhitelistConfig.builder()
            .id(3L)
            .customerId("customer-001")
            .whitelistType("COMBINED")
            .ipAddress("192.168.1.10")
            .macAddress("00:11:22:33:44:55")
            .reason("管理员设备绑定")
            .isActive(true)
            .createdAt(Instant.now())
            .build();
        
        // 过期白名单
        expiredWhitelist = WhitelistConfig.builder()
            .id(4L)
            .customerId("customer-001")
            .whitelistType("IP")
            .ipAddress("192.168.1.200")
            .reason("临时测试设备")
            .isActive(true)
            .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
            .createdAt(Instant.now().minus(8, ChronoUnit.DAYS))
            .build();
    }
    
    // ==================== IP白名单检查测试 ====================
    
    @Test
    @DisplayName("IP在白名单中应返回true")
    void testIsIpWhitelisted_True() {
        // Given
        when(repository.isIpWhitelisted("customer-001", "192.168.1.10"))
            .thenReturn(true);
        
        // When
        boolean result = whitelistService.isIpWhitelisted("customer-001", "192.168.1.10");
        
        // Then
        assertTrue(result);
        verify(repository, times(1)).isIpWhitelisted("customer-001", "192.168.1.10");
    }
    
    @Test
    @DisplayName("IP不在白名单中应返回false")
    void testIsIpWhitelisted_False() {
        // Given
        when(repository.isIpWhitelisted("customer-001", "8.8.8.8"))
            .thenReturn(false);
        
        // When
        boolean result = whitelistService.isIpWhitelisted("customer-001", "8.8.8.8");
        
        // Then
        assertFalse(result);
    }
    
    @Test
    @DisplayName("空IP应返回false")
    void testIsIpWhitelisted_NullIp() {
        // When
        boolean result1 = whitelistService.isIpWhitelisted("customer-001", null);
        boolean result2 = whitelistService.isIpWhitelisted(null, "192.168.1.10");
        
        // Then
        assertFalse(result1);
        assertFalse(result2);
        verify(repository, never()).isIpWhitelisted(anyString(), anyString());
    }
    
    // ==================== MAC白名单检查测试 ====================
    
    @Test
    @DisplayName("MAC在白名单中应返回true")
    void testIsMacWhitelisted_True() {
        // Given
        when(repository.isMacWhitelisted("customer-001", "00:11:22:33:44:55"))
            .thenReturn(true);
        
        // When
        boolean result = whitelistService.isMacWhitelisted("customer-001", "00:11:22:33:44:55");
        
        // Then
        assertTrue(result);
    }
    
    @Test
    @DisplayName("MAC不在白名单中应返回false")
    void testIsMacWhitelisted_False() {
        // Given
        when(repository.isMacWhitelisted("customer-001", "AA:BB:CC:DD:EE:FF"))
            .thenReturn(false);
        
        // When
        boolean result = whitelistService.isMacWhitelisted("customer-001", "AA:BB:CC:DD:EE:FF");
        
        // Then
        assertFalse(result);
    }
    
    @Test
    @DisplayName("空MAC应返回false")
    void testIsMacWhitelisted_NullMac() {
        // When
        boolean result1 = whitelistService.isMacWhitelisted("customer-001", null);
        boolean result2 = whitelistService.isMacWhitelisted(null, "00:11:22:33:44:55");
        
        // Then
        assertFalse(result1);
        assertFalse(result2);
        verify(repository, never()).isMacWhitelisted(anyString(), anyString());
    }
    
    // ==================== 组合白名单检查测试 ====================
    
    @Test
    @DisplayName("IP+MAC组合在白名单中应返回true")
    void testIsCombinationWhitelisted_True() {
        // Given
        when(repository.isCombinationWhitelisted("customer-001", "192.168.1.10", "00:11:22:33:44:55"))
            .thenReturn(true);
        
        // When
        boolean result = whitelistService.isCombinationWhitelisted(
            "customer-001", "192.168.1.10", "00:11:22:33:44:55"
        );
        
        // Then
        assertTrue(result);
    }
    
    @Test
    @DisplayName("IP+MAC组合不在白名单中应返回false")
    void testIsCombinationWhitelisted_False() {
        // Given
        when(repository.isCombinationWhitelisted("customer-001", "192.168.1.10", "AA:BB:CC:DD:EE:FF"))
            .thenReturn(false);
        
        // When
        boolean result = whitelistService.isCombinationWhitelisted(
            "customer-001", "192.168.1.10", "AA:BB:CC:DD:EE:FF"
        );
        
        // Then
        assertFalse(result);
    }
    
    // ==================== 综合白名单检查测试 ====================
    
    @Test
    @DisplayName("组合白名单命中应返回true")
    void testIsWhitelisted_CombinationMatch() {
        // Given
        when(repository.isCombinationWhitelisted("customer-001", "192.168.1.10", "00:11:22:33:44:55"))
            .thenReturn(true);
        
        // When
        boolean result = whitelistService.isWhitelisted(
            "customer-001", "192.168.1.10", "00:11:22:33:44:55"
        );
        
        // Then
        assertTrue(result);
        // 组合匹配后不再检查IP和MAC
        verify(repository, times(1)).isCombinationWhitelisted(anyString(), anyString(), anyString());
        verify(repository, never()).isIpWhitelisted(anyString(), anyString());
        verify(repository, never()).isMacWhitelisted(anyString(), anyString());
    }
    
    @Test
    @DisplayName("IP白名单命中应返回true")
    void testIsWhitelisted_IpMatch() {
        // Given
        when(repository.isCombinationWhitelisted(anyString(), anyString(), anyString()))
            .thenReturn(false);
        when(repository.isIpWhitelisted("customer-001", "192.168.1.10"))
            .thenReturn(true);
        
        // When
        boolean result = whitelistService.isWhitelisted(
            "customer-001", "192.168.1.10", "AA:BB:CC:DD:EE:FF"
        );
        
        // Then
        assertTrue(result);
        verify(repository, times(1)).isCombinationWhitelisted(anyString(), anyString(), anyString());
        verify(repository, times(1)).isIpWhitelisted(anyString(), anyString());
        verify(repository, never()).isMacWhitelisted(anyString(), anyString());
    }
    
    @Test
    @DisplayName("MAC白名单命中应返回true")
    void testIsWhitelisted_MacMatch() {
        // Given
        when(repository.isCombinationWhitelisted(anyString(), anyString(), anyString()))
            .thenReturn(false);
        when(repository.isIpWhitelisted(anyString(), anyString()))
            .thenReturn(false);
        when(repository.isMacWhitelisted("customer-001", "00:11:22:33:44:55"))
            .thenReturn(true);
        
        // When
        boolean result = whitelistService.isWhitelisted(
            "customer-001", "8.8.8.8", "00:11:22:33:44:55"
        );
        
        // Then
        assertTrue(result);
        verify(repository, times(1)).isCombinationWhitelisted(anyString(), anyString(), anyString());
        verify(repository, times(1)).isIpWhitelisted(anyString(), anyString());
        verify(repository, times(1)).isMacWhitelisted(anyString(), anyString());
    }
    
    @Test
    @DisplayName("都不在白名单中应返回false")
    void testIsWhitelisted_NoMatch() {
        // Given
        when(repository.isCombinationWhitelisted(anyString(), anyString(), anyString()))
            .thenReturn(false);
        when(repository.isIpWhitelisted(anyString(), anyString()))
            .thenReturn(false);
        when(repository.isMacWhitelisted(anyString(), anyString()))
            .thenReturn(false);
        
        // When
        boolean result = whitelistService.isWhitelisted(
            "customer-001", "8.8.8.8", "AA:BB:CC:DD:EE:FF"
        );
        
        // Then
        assertFalse(result);
    }
    
    // ==================== 白名单管理测试 ====================
    
    @Test
    @DisplayName("查询有效白名单应返回激活且未过期的配置")
    void testGetActiveWhitelist() {
        // Given
        List<WhitelistConfig> activeList = Arrays.asList(ipWhitelist, macWhitelist);
        when(repository.findActiveByCustomerId("customer-001"))
            .thenReturn(activeList);
        
        // When
        List<WhitelistConfig> result = whitelistService.getActiveWhitelist("customer-001");
        
        // Then
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(WhitelistConfig::getIsActive));
    }
    
    @Test
    @DisplayName("添加白名单应保存并清除缓存")
    void testAddWhitelist() {
        // Given
        when(repository.save(any(WhitelistConfig.class)))
            .thenReturn(ipWhitelist);
        
        // When
        WhitelistConfig result = whitelistService.addWhitelist(ipWhitelist);
        
        // Then
        assertNotNull(result);
        assertEquals("192.168.1.10", result.getIpAddress());
        verify(repository, times(1)).save(any(WhitelistConfig.class));
    }
    
    @Test
    @DisplayName("删除白名单应调用repository删除方法")
    void testDeleteWhitelist() {
        // When
        whitelistService.deleteWhitelist(1L);
        
        // Then
        verify(repository, times(1)).deleteById(1L);
    }
    
    @Test
    @DisplayName("禁用白名单应设置isActive为false")
    void testDisableWhitelist() {
        // Given
        when(repository.findById(1L))
            .thenReturn(Optional.of(ipWhitelist));
        when(repository.save(any(WhitelistConfig.class)))
            .thenReturn(ipWhitelist);
        
        // When
        whitelistService.disableWhitelist(1L);
        
        // Then
        verify(repository, times(1)).findById(1L);
        verify(repository, times(1)).save(any(WhitelistConfig.class));
    }
    
    // ==================== 过期管理测试 ====================
    
    @Test
    @DisplayName("查询即将过期的白名单应返回7天内过期的配置")
    void testGetExpiringSoon() {
        // Given
        WhitelistConfig expiringSoon = WhitelistConfig.builder()
            .id(5L)
            .customerId("customer-001")
            .whitelistType("IP")
            .ipAddress("192.168.1.201")
            .expiresAt(Instant.now().plus(5, ChronoUnit.DAYS))
            .isActive(true)
            .build();
        
        when(repository.findExpiringSoon(any(Instant.class)))
            .thenReturn(Arrays.asList(expiringSoon));
        
        // When
        List<WhitelistConfig> result = whitelistService.getExpiringSoon();
        
        // Then
        assertEquals(1, result.size());
        assertTrue(result.get(0).getExpiresAt().isAfter(Instant.now()));
    }
    
    @Test
    @DisplayName("清理过期白名单应禁用已过期的配置")
    void testCleanupExpiredWhitelist() {
        // Given
        when(repository.findExpired())
            .thenReturn(Arrays.asList(expiredWhitelist));
        when(repository.save(any(WhitelistConfig.class)))
            .thenReturn(expiredWhitelist);
        
        // When
        whitelistService.cleanupExpiredWhitelist();
        
        // Then
        verify(repository, times(1)).findExpired();
        verify(repository, times(1)).save(any(WhitelistConfig.class));
    }
    
    @Test
    @DisplayName("无过期白名单时不应执行任何操作")
    void testCleanupExpiredWhitelist_NoExpired() {
        // Given
        when(repository.findExpired())
            .thenReturn(Arrays.asList());
        
        // When
        whitelistService.cleanupExpiredWhitelist();
        
        // Then
        verify(repository, times(1)).findExpired();
        verify(repository, never()).save(any(WhitelistConfig.class));
    }
    
    // ==================== 初始化测试 ====================
    
    @Test
    @DisplayName("已有白名单时应输出统计信息")
    void testInitializeWhitelist_HasConfigs() {
        // Given
        when(repository.count()).thenReturn(10L);
        when(repository.findAll()).thenReturn(Arrays.asList(
            ipWhitelist, macWhitelist, combinedWhitelist
        ));
        
        // When
        whitelistService.initializeWhitelist();
        
        // Then
        verify(repository, times(1)).count();
        verify(repository, times(1)).findAll();
    }
    
    @Test
    @DisplayName("无白名单时应警告")
    void testInitializeWhitelist_NoConfigs() {
        // Given
        when(repository.count()).thenReturn(0L);
        
        // When
        whitelistService.initializeWhitelist();
        
        // Then
        verify(repository, times(1)).count();
        verify(repository, never()).findAll();
    }
}
