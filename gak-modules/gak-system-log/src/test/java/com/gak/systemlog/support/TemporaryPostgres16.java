package com.gak.systemlog.support;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 只管理本测试创建的空 PG16 集群，不接受外部数据库地址或凭据。 */
public final class TemporaryPostgres16 implements AutoCloseable {

    public static final String OWNER_ROLE = "sl_test_owner";
    private static final Path BIN = Path.of("/usr/lib/postgresql/16/bin");
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);
    private static final int BACKEND_PORT = 8080;
    private static final int PROTECTED_DATABASE_PORT = 55432;

    private final Path directory;
    private final Path cluster;
    private final int port;

    private TemporaryPostgres16(Path directory, int port) {
        this.directory = directory;
        this.cluster = directory.resolve("cluster");
        this.port = port;
    }

    /** 缺少二进制时由 JUnit 明确标为跳过；启动或套接字失败必须报告失败。 */
    public static TemporaryPostgres16 start(Path target) throws Exception {
        for (String executable : List.of("initdb", "pg_ctl", "postgres")) {
            assumeTrue(Files.isExecutable(BIN.resolve(executable)),
                    "跳过写入集成测试：缺少 PostgreSQL 16 二进制 " + BIN.resolve(executable));
        }
        int port = availablePort();
        Files.createDirectories(target);
        TemporaryPostgres16 postgres = new TemporaryPostgres16(
                Files.createTempDirectory(target, "system-log-pg16-"), port);
        try {
            postgres.command("initdb", "-D", postgres.cluster.toString(), "-U", OWNER_ROLE,
                    "-A", "trust", "--no-locale", "--encoding=UTF8");
            postgres.command("pg_ctl", "-D", postgres.cluster.toString(), "-w", "-t", "15",
                    "-l", postgres.directory.resolve("postgres.log").toString(), "-o",
                    "-h 127.0.0.1 -p " + port + " -c unix_socket_directories=''", "start");
            return postgres;
        } catch (Exception | Error failure) {
            try {
                postgres.close();
            } catch (Exception cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    public String jdbcUrl() {
        return "jdbc:postgresql://127.0.0.1:" + port + "/postgres?connectTimeout=3&socketTimeout=5";
    }

    /** 用真实 psql 安装脚本，保证 psql 变量、事务和前置检查均参与 JDBC 集成验证。 */
    public void executeScript(Path script, String... variables) throws Exception {
        List<String> arguments = new ArrayList<>(List.of("-X", "-h", "127.0.0.1", "-p",
                Integer.toString(port), "-U", OWNER_ROLE, "-d", "postgres", "-v", "ON_ERROR_STOP=1"));
        for (String variable : variables) {
            arguments.add("-v");
            arguments.add(variable);
        }
        arguments.addAll(List.of("-f", script.toAbsolutePath().toString()));
        command("psql", arguments.toArray(String[]::new));
    }

    private static int availablePort() throws IOException {
        int candidate;
        do {
            try (ServerSocket reservation = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
                candidate = reservation.getLocalPort();
            }
        } while (candidate == BACKEND_PORT || candidate == PROTECTED_DATABASE_PORT);
        return candidate;
    }

    private void command(String executable, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(BIN.resolve(executable).toString());
        command.addAll(List.of(arguments));
        Path output = directory.resolve(executable + ".log");
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(output.toFile()));
        // 不继承宿主 PG 连接设置或凭据文件设置，所有连接仅指向本测试实例。
        builder.environment().keySet().removeIf(name -> name.startsWith("PG"));
        builder.environment().put("LC_ALL", "C");
        Process process = builder.start();
        try {
            if (!process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new IOException(executable + " 超时\n" + diagnostics(output));
            }
            if (process.exitValue() != 0) {
                throw new IOException(executable + " 退出码=" + process.exitValue() + "\n" + diagnostics(output));
            }
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private String diagnostics(Path commandLog) throws IOException {
        Path serverLog = directory.resolve("postgres.log");
        return Files.readString(commandLog)
                + (Files.exists(serverLog) ? "\n" + Files.readString(serverLog) : "");
    }

    @Override
    public void close() throws Exception {
        // 仅对私有集群目录发 stop，停库失败时保留目录以免删掉仍在使用的数据库文件。
        if (Files.exists(cluster.resolve("postmaster.pid"))) {
            command("pg_ctl", "-D", cluster.toString(), "-w", "-t", "15", "-m", "immediate", "stop");
        }
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }
}
