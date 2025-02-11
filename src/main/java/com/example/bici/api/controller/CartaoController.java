package com.example.bici.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.*;

@RestController
@RequestMapping("/api/cartao")
public class CartaoController {

    private final Logger logger = LoggerFactory.getLogger(CartaoController.class);

    @GetMapping("/usuarios/autenticar")
    public ResponseEntity<Object> autenticarUsuario(@RequestParam String numeroDoCartao) {
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://mysql.bicicletariooken.kinghost.net:3306/bicicletariook", "bicicletariook", "u88w1OGL5Bbu")) {
            String consulta = "SELECT cpf, liberado, bloqueado_desbloqueado FROM usuario WHERE numero_do_cartao = ?";
            try (PreparedStatement statement = connection.prepareStatement(consulta)) {
                statement.setString(1, numeroDoCartao);
                try (ResultSet resultSet = statement.executeQuery()) {
                    boolean cartaoBloqueado = false;
                    boolean cartaoLiberado = false;
                    String cpf = null;

                    if (resultSet.next()) {
                        cpf = resultSet.getString("cpf");
                        int liberado = resultSet.getInt("liberado");
                        int bloqueadoDesbloqueado = resultSet.getInt("bloqueado_desbloqueado");

                        if (liberado == 0 && bloqueadoDesbloqueado == 0) {
                            cartaoLiberado = true;
                        } else {
                            cartaoBloqueado = true;
                        }
                    }

                    String statusAutenticacao;
                    if (cartaoBloqueado) {
                        statusAutenticacao = "Cartão Bloqueado";
                        registrarAutenticacao(connection, numeroDoCartao, cpf, statusAutenticacao);
                        registrarAuditoria(connection, numeroDoCartao, cpf, "Tentativa de autenticação com cartão bloqueado");
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(statusAutenticacao);
                    }

                    if (cartaoLiberado || autenticarUsuarioDiretamente(numeroDoCartao)) {
                        statusAutenticacao = "Usuário Autenticado";
                        registrarAutenticacao(connection, numeroDoCartao, cpf, statusAutenticacao);
                        registrarAuditoria(connection, numeroDoCartao, cpf, "Autenticação bem-sucedida");
                        return ResponseEntity.ok(statusAutenticacao);
                    }

                    statusAutenticacao = "Usuário não Encontrado";
                    registrarAutenticacao(connection, numeroDoCartao, null, statusAutenticacao);
                    registrarAuditoria(connection, numeroDoCartao, null, "Tentativa de autenticação com usuário não encontrado");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(statusAutenticacao);
                }
            }
        } catch (SQLException e) {
            logger.error("Ocorreu um erro durante a autenticação.", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ocorreu um erro durante a autenticação.");
        }
    }

    private boolean autenticarUsuarioDiretamente(String numeroDoCartao) {
        // Lógica de autenticação direta, se necessário
        return true; // ou false, dependendo da lógica
    }

    private void registrarAutenticacao(Connection connection, String numeroDoCartao, String cpf, String status) {
        String chamadaProcedure = "{ CALL registrar_autenticacao(?,?,?) }";
        try (CallableStatement callableStatement = connection.prepareCall(chamadaProcedure)) {
            callableStatement.setString(1, numeroDoCartao);
            callableStatement.setString(2, cpf);
            callableStatement.setString(3, status);
            callableStatement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erro ao registrar autenticação.", e);
        }
    }

    private void registrarAuditoria(Connection connection, String numeroDoCartao, String cpf, String acao) {
        String chamadaProcedure = "{ CALL registrar_auditoria(?, ?, ?) }";
        try (CallableStatement callableStatement = connection.prepareCall(chamadaProcedure)) {
            callableStatement.setString(1, numeroDoCartao);
            callableStatement.setString(2, cpf);
            callableStatement.setString(3, acao);
            callableStatement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erro ao registrar auditoria.", e);
        }
    }

    @GetMapping("/verificarcreditos")
    public ResponseEntity<Object> verificarCreditos(@RequestParam String numeroDoCartao) {
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://mysql.bicicletariooken.kinghost.net:3306/bicicletariook", "bicicletariook", "u88w1OGL5Bbu")) {
            String consulta = "SELECT creditos_restantes, cpf FROM usuario WHERE numero_do_cartao = ?";
            try (PreparedStatement statement = connection.prepareStatement(consulta)) {
                statement.setString(1, numeroDoCartao);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        int creditos = resultSet.getInt("creditos_restantes");
                        String cpf = resultSet.getString("cpf");
                        registrarVerificacaoCreditos(connection, numeroDoCartao, creditos, cpf);
                        registrarAuditoria(connection, numeroDoCartao, cpf, "Verificação de créditos");
                        return ResponseEntity.ok().body("Créditos restantes do usuário: " + creditos);
                    } else {
                        registrarAuditoria(connection, numeroDoCartao, null, "Tentativa de verificação de créditos com usuário não encontrado");
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuário não encontrado.");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao acessar o banco de dados.", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao acessar o banco de dados: " + e.getMessage());
        }
    }

    private void registrarVerificacaoCreditos(Connection connection, String numeroDoCartao, int creditosRestantes, String cpf) {
        String chamadaProcedure = "{ CALL registrar_verificacao_creditos(?, ?, ?) }";
        try (CallableStatement callableStatement = connection.prepareCall(chamadaProcedure)) {
            callableStatement.setString(1, numeroDoCartao);
            callableStatement.setInt(2, creditosRestantes);
            callableStatement.setString(3, cpf);
            callableStatement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erro ao registrar verificação de créditos.", e);
        }
    }

    @PostMapping("/usuarios/utilizarcredito")
    public ResponseEntity<Object> utilizarCredito(@RequestParam String numeroDoCartao) {
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://mysql.bicicletariooken.kinghost.net:3306/bicicletariook", "bicicletariook", "u88w1OGL5Bbu")) {
            String consultaCreditos = "SELECT creditos_restantes, cpf FROM usuario WHERE numero_do_cartao = ?";
            try (PreparedStatement statement = connection.prepareStatement(consultaCreditos)) {
                statement.setString(1, numeroDoCartao);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        int creditos = resultSet.getInt("creditos_restantes");
                        String cpf = resultSet.getString("cpf");
                        if (creditos > 0) {
                            String consultaAtualizacao = "UPDATE usuario SET creditos_restantes = creditos_restantes - 1 WHERE numero_do_cartao = ? AND creditos_restantes > 0";
                            try (PreparedStatement updateStatement = connection.prepareStatement(consultaAtualizacao)) {
                                updateStatement.setString(1, numeroDoCartao);
                                int linhasAfetadas = updateStatement.executeUpdate();
                                if (linhasAfetadas > 0) {
                                    registrarUtilizacaoCredito(connection, numeroDoCartao, creditos - 1, cpf);
                                    registrarAuditoria(connection, numeroDoCartao, cpf, "Utilização de crédito");
                                    return ResponseEntity.ok().body("Crédito utilizado com sucesso.");
                                } else {
                                    registrarAuditoria(connection, numeroDoCartao, cpf, "Erro ao utilizar crédito - Atualização falhou");
                                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao utilizar crédito.");
                                }
                            }
                        } else {
                            registrarAuditoria(connection, numeroDoCartao, cpf, "Tentativa de utilizar crédito sem créditos suficientes");
                            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Não há créditos suficientes para utilizar.");
                        }
                    } else {
                        registrarAuditoria(connection, numeroDoCartao, null, "Tentativa de utilização de crédito com usuário não encontrado");
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuário não encontrado.");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao acessar o banco de dados.", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao acessar o banco de dados: " + e.getMessage());
        }
    }

    private void registrarUtilizacaoCredito(Connection connection, String numeroDoCartao, int creditosRestantes, String cpf) {
        String chamadaProcedure = "{ CALL registrar_utilizacao_credito(?, ?, ?) }";
        try (CallableStatement callableStatement = connection.prepareCall(chamadaProcedure)) {
            callableStatement.setString(1, numeroDoCartao);
            callableStatement.setInt(2, creditosRestantes);
            callableStatement.setString(3, cpf);
            callableStatement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erro ao registrar utilização de crédito.", e);
        }
    }
}