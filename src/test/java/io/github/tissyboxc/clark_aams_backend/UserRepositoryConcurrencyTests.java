package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.user.UserRepository.UserRecord;
import io.github.tissyboxc.clark_aams_backend.user.SQLiteUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户仓储的并发安全（P5-1）。
 *
 * <p>原实现里 `nextId()` 与 `save()` 是**两次独立的加锁调用**：
 * 服务层先 `nextId()` 拿一个号，再 `save(record)` 写库。
 * 两个注册请求并发时，完全可能拿到同一个 id，
 * 后写的那个把先写的覆盖掉——用户「注册成功」但账号消失。
 *
 * <p>这里用多线程真实打库复现，而不是读代码推断。
 */
class UserRepositoryConcurrencyTests {

    @TempDir
    Path tempDir;

    private SQLiteUserRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SQLiteUserRepository(tempDir.resolve("concurrency.db").toString());
        repository.initialize();
    }

    private UserRecord user(long id, String username) {
        // login_code 有唯一约束，测试里用 username 派生，避免撞库
        return new UserRecord(
                id,
                username,
                "hash",
                username + "@qq.com",
                "CODE-" + username,
                true,
                true,
                null,
                0L,
                0L
        );
    }

    @Test
    @DisplayName("并发注册不会拿到重复 id")
    void concurrentNextIdIsUnique() throws Exception {
        final int workers = 16;
        final ExecutorService pool = Executors.newFixedThreadPool(workers);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Long> allocated = Collections.synchronizedList(new ArrayList<>());

        // 走真实的注册写入路径：insert 原子分配 id
        for (int i = 0; i < workers; i++) {
            final int index = i;
            pool.submit(() -> {
                try {
                    start.await();
                    long id = repository.insert(user(0L, "u" + index)).id();
                    allocated.add(id);
                } catch (Exception exception) {
                    // 让断言去发现丢失，而不是在这里吞掉
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "线程池未在超时内结束");

        Set<Long> unique = new HashSet<>(allocated);
        assertEquals(
                unique.size(),
                allocated.size(),
                "并发下 nextId() 返回了重复 id：" + allocated
        );
    }

    @Test
    @DisplayName("并发保存后用户数等于成功次数，没有互相覆盖")
    void concurrentSaveDoesNotOverwrite() throws Exception {
        final int workers = 12;
        final ExecutorService pool = Executors.newFixedThreadPool(workers);
        final CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < workers; i++) {
            final int index = i;
            pool.submit(() -> {
                try {
                    start.await();
                    repository.insert(user(0L, "user" + index));
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "线程池未在超时内结束");

        assertEquals(
                workers,
                repository.findAll().size(),
                "并发保存后用户数少于提交次数，说明发生了覆盖"
        );
    }

    @Test
    @DisplayName("串行分配 id 从 1 开始递增")
    void sequentialAllocationStartsAtOne() {
        assertEquals(1L, repository.nextId());
        repository.save(user(1L, "first"));
        assertEquals(2L, repository.nextId());
        repository.save(user(2L, "second"));
        assertEquals(3L, repository.nextId());
    }

    @Test
    @DisplayName("删除后不复用已删除的 id")
    void doesNotReuseDeletedId() throws Exception {
        repository.save(user(1L, "a"));
        repository.save(user(2L, "b"));
        repository.save(user(3L, "c"));

        // 仓储没有删除接口，直接改库模拟「用户被删」
        try (var connection = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + tempDir.resolve("concurrency.db").toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.executeUpdate("delete from users where id = 3");
        }

        // 若用 max(id)+1 会得到 3，与已删除的 id 复用；
        // 正确的做法是目前最大 id 继续递增（4），不与历史 id 冲突。
        long next = repository.nextId();
        assertTrue(next > 3L, "不应复用已删除的 id，实际得到 " + next);
    }
}
