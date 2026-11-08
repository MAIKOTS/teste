package app;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClienteGitHub {

    private static final String API = "https://api.github.com";
    private final String token;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());

    public interface Callback<T> {
        void sucesso(T resultado);
        void erro(String mensagem);
    }

    public ClienteGitHub(String token) { this.token = token; }

    // ---------- Infra ----------
    private HttpURLConnection abrir(String metodo, String caminho) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(API + caminho).openConnection();
        c.setRequestMethod(metodo);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        c.setRequestProperty("User-Agent", "Gitigin-Studio");
        c.setConnectTimeout(20000);
        c.setReadTimeout(20000);
        return c;
    }

    private static String ler(HttpURLConnection c) throws IOException {
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = br.readLine()) != null) sb.append(l).append('\n');
            return sb.toString();
        }
    }

    private static void escreverJson(HttpURLConnection c, JSONObject body) throws IOException {
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = c.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private interface Acao<T> { T executar() throws Exception; }

    private <T> void rodar(final Acao<T> acao, final Callback<T> cb) {
        executor.execute(() -> {
            try {
                final T r = acao.executar();
                ui.post(() -> cb.sucesso(r));
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                ui.post(() -> cb.erro(msg));
            }
        });
    }

    private static void checar(HttpURLConnection c, String body) throws IOException {
        int code = c.getResponseCode();
        if (code / 100 != 2) throw new IOException("HTTP " + code + ": " + body);
    }

    // ---------- Usuário ----------
    public void obterUsuario(Callback<JSONObject> cb) {
        rodar(() -> {
            HttpURLConnection c = abrir("GET", "/user");
            String body = ler(c);
            checar(c, body);
            return new JSONObject(body);
        }, cb);
    }

    // ---------- Repositórios ----------
    public void listarRepositorios(Callback<JSONArray> cb) {
        rodar(() -> {
            HttpURLConnection c = abrir("GET", "/user/repos?per_page=100&sort=updated");
            String body = ler(c);
            checar(c, body);
            return new JSONArray(body);
        }, cb);
    }

    public void criarRepositorio(String nome, String descricao, boolean privado, Callback<JSONObject> cb) {
        rodar(() -> {
            HttpURLConnection c = abrir("POST", "/user/repos");
            JSONObject b = new JSONObject();
            b.put("name", nome);
            b.put("description", descricao);
            b.put("private", privado);
            b.put("auto_init", true);
            escreverJson(c, b);
            String resp = ler(c);
            checar(c, resp);
            return new JSONObject(resp);
        }, cb);
    }

    public void editarRepositorio(String dono, String nome, String novaDescricao,
                                  Boolean privado, Callback<JSONObject> cb) {
        rodar(() -> {
            HttpURLConnection c = abrir("PATCH", "/repos/" + dono + "/" + nome);
            JSONObject b = new JSONObject();
            if (novaDescricao != null) b.put("description", novaDescricao);
            if (privado != null) b.put("private", privado);
            escreverJson(c, b);
            String resp = ler(c);
            checar(c, resp);
            return new JSONObject(resp);
        }, cb);
    }

    public void deletarRepositorio(String dono, String nome, Callback<Boolean> cb) {
        rodar(() -> {
            HttpURLConnection c = abrir("DELETE", "/repos/" + dono + "/" + nome);
            checar(c, ler(c));
            return true;
        }, cb);
    }

    // ---------- Conteúdo ----------
    public void listarConteudo(String dono, String repo, String caminho, Callback<JSONArray> cb) {
        rodar(() -> {
            String p = (caminho == null || caminho.isEmpty()) ? "" : "/" + caminho;
            HttpURLConnection c = abrir("GET", "/repos/" + dono + "/" + repo + "/contents" + p);
            String body = ler(c);
            int code = c.getResponseCode();
            if (code == 404) return new JSONArray(); // repo vazio
            checar(c, body);
            if (body.trim().startsWith("{")) {
                JSONArray a = new JSONArray();
                a.put(new JSONObject(body));
                return a;
            }
            return new JSONArray(body);
        }, cb);
    }

    public static class Arquivo {
        public String sha;
        public String conteudo;
    }

    public void obterArquivo(String dono, String repo, String caminho, Callback<Arquivo> cb) {
        rodar(() -> {
            HttpURLConnection c = abrir("GET", "/repos/" + dono + "/" + repo + "/contents/" + caminho);
            String body = ler(c);
            checar(c, body);
            JSONObject o = new JSONObject(body);
            Arquivo a = new Arquivo();
            a.sha = o.getString("sha");
            String b64 = o.getString("content").replaceAll("\\s", "");
            byte[] dec = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
            a.conteudo = new String(dec, StandardCharsets.UTF_8);
            return a;
        }, cb);
    }

    public void salvarArquivo(String dono, String repo, String caminho, String conteudo,
                              String shaExistente, String mensagem, Callback<JSONObject> cb) {
        rodar(() -> {
            HttpURLConnection c = abrir("PUT", "/repos/" + dono + "/" + repo + "/contents/" + caminho);
            JSONObject b = new JSONObject();
            b.put("message", mensagem);
            b.put("content", android.util.Base64.encodeToString(
                    conteudo.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP));
            if (shaExistente != null) b.put("sha", shaExistente);
            escreverJson(c, b);
            String resp = ler(c);
            checar(c, resp);
            return new JSONObject(resp);
        }, cb);
    }

    public void deletarArquivo(String dono, String repo, String caminho, String sha,
                               String mensagem, Callback<Boolean> cb) {
        rodar(() -> {
            HttpURLConnection c = abrir("DELETE", "/repos/" + dono + "/" + repo + "/contents/" + caminho);
            JSONObject b = new JSONObject();
            b.put("message", mensagem);
            b.put("sha", sha);
            escreverJson(c, b);
            checar(c, ler(c));
            return true;
        }, cb);
    }
    
    // ---------- Upload de Arquivos (Bytes) ----------
public void obterShaArquivo(String dono, String repo, String caminho, Callback<String> cb) {
    rodar(() -> {
        HttpURLConnection c = abrir("GET", "/repos/" + dono + "/" + repo + "/contents/" + caminho);
        String body = ler(c);
        int code = c.getResponseCode();
        if (code == 404) throw new IOException("404");
        checar(c, body);
        return new JSONObject(body).getString("sha");
    }, cb);
}

public void salvarArquivoBytes(String dono, String repo, String caminho, byte[] bytes,
                               String shaExistente, String mensagem, Callback<JSONObject> cb) {
    rodar(() -> {
        HttpURLConnection c = abrir("PUT", "/repos/" + dono + "/" + repo + "/contents/" + caminho);
        JSONObject b = new JSONObject();
        b.put("message", mensagem);
        b.put("content", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP));
        if (shaExistente != null) b.put("sha", shaExistente);
        escreverJson(c, b);
        String resp = ler(c);
        checar(c, resp);
        return new JSONObject(resp);
    }, cb);
}
}