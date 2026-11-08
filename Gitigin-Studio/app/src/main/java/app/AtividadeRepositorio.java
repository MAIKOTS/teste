package app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.documentfile.provider.DocumentFile;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class AtividadeRepositorio extends Activity {

    private ClienteGitHub cliente;
    private String dono, repo, token, caminhoAtual = "";
    private ListView lista;
    private JSONArray itens = new JSONArray();

    // Códigos para identificar o retorno do SAF
    private static final int REQ_UPLOAD_FILES = 100;
    private static final int REQ_UPLOAD_FOLDER = 200;

    // Classe interna para guardar os dados do upload
    private static class ArquivoUpload {
        Uri uri;
        String caminhoRelativo;
        ArquivoUpload(Uri uri, String caminho) { this.uri = uri; this.caminhoRelativo = caminho; }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        token  = getIntent().getStringExtra("token");
        dono   = getIntent().getStringExtra("dono");
        repo   = getIntent().getStringExtra("repo");
        cliente = new ClienteGitHub(token);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout barra = new LinearLayout(this);
        barra.setOrientation(LinearLayout.HORIZONTAL);
        barra.setPadding(20, 20, 20, 20);

        Button voltar = new Button(this); voltar.setText("←"); barra.addView(voltar);

        TextView titulo = new TextView(this);
        titulo.setText(dono + "/" + repo);
        titulo.setTextSize(18);
        titulo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        barra.addView(titulo);

        Button btnAdd = new Button(this); btnAdd.setText("+ ADD"); barra.addView(btnAdd);
        root.addView(barra);

        lista = new ListView(this);
        lista.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(lista);

        voltar.setOnClickListener(v -> {
            if (caminhoAtual.isEmpty()) finish();
            else {
                caminhoAtual = caminhoAtual.contains("/")
                        ? caminhoAtual.substring(0, caminhoAtual.lastIndexOf('/')) : "";
                carregar();
            }
        });

        btnAdd.setOnClickListener(v -> {
            String[] opcoes = {"Novo arquivo em branco", "Upload de Arquivo(s)", "Upload de Pasta"};
            new AlertDialog.Builder(this)
                    .setTitle("Adicionar")
                    .setItems(opcoes, (d, w) -> {
                        if (w == 0) dialogoNovoArquivo();
                        else if (w == 1) selecionarArquivos();
                        else if (w == 2) selecionarPasta();
                    }).show();
        });

        lista.setOnItemClickListener((p, v, pos, id) -> {
            try {
                JSONObject item = itens.getJSONObject(pos);
                String tipo = item.getString("type");
                String nome = item.getString("name");
                if ("dir".equals(tipo)) {
                    caminhoAtual = caminhoAtual.isEmpty() ? nome : caminhoAtual + "/" + nome;
                    carregar();
                } else if ("file".equals(tipo)) {
                    Intent i = new Intent(this, AtividadeEditor.class);
                    i.putExtra("token", token);
                    i.putExtra("dono", dono);
                    i.putExtra("repo", repo);
                    i.putExtra("caminho", caminhoAtual.isEmpty() ? nome : caminhoAtual + "/" + nome);
                    startActivity(i);
                }
            } catch (Exception ignored) {}
        });

        setContentView(root);
        carregar();
    }

    @Override protected void onResume() { super.onResume(); if (cliente != null) carregar(); }

    private void carregar() {
        cliente.listarConteudo(dono, repo, caminhoAtual, new ClienteGitHub.Callback<JSONArray>() {
            @Override public void sucesso(JSONArray arr) {
                itens = arr;
                BaseAdapter ad = new BaseAdapter() {
                    @Override public int getCount() { return itens.length(); }
                    @Override public Object getItem(int pos) { return pos; }
                    @Override public long getItemId(int pos) { return pos; }
                    @Override public View getView(int pos, View cv, ViewGroup parent) {
                        View v = cv;
                        if (v == null) v = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
                        TextView t = v.findViewById(android.R.id.text1);
                        try {
                            JSONObject o = itens.getJSONObject(pos);
                            t.setText(("dir".equals(o.getString("type")) ? "📁 " : "📄 ") + o.getString("name"));
                        } catch (Exception ignored) {}
                        return v;
                    }
                };
                lista.setAdapter(ad);
            }
            @Override public void erro(String m) {
                Toast.makeText(AtividadeRepositorio.this, "Erro: " + m, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void dialogoNovoArquivo() {
        EditText nome = new EditText(this);
        nome.setHint("nome (ex: README.md)");
        new AlertDialog.Builder(this)
                .setTitle("Novo arquivo")
                .setView(nome)
                .setPositiveButton("Criar", (d, w) -> {
                    String n = nome.getText().toString().trim();
                    if (n.isEmpty()) return;
                    Intent i = new Intent(this, AtividadeEditor.class);
                    i.putExtra("token", token);
                    i.putExtra("dono", dono);
                    i.putExtra("repo", repo);
                    i.putExtra("caminho", caminhoAtual.isEmpty() ? n : caminhoAtual + "/" + n);
                    i.putExtra("novo", true);
                    startActivity(i);
                })
                .setNegativeButton("Cancelar", null).show();
    }

    // ---------- Lógica de Upload ----------

    private void selecionarArquivos() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); // Permite selecionar vários
        startActivityForResult(intent, REQ_UPLOAD_FILES);
    }

    private void selecionarPasta() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQ_UPLOAD_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        List<ArquivoUpload> arquivos = new ArrayList<>();

        if (requestCode == REQ_UPLOAD_FILES) {
            if (data.getClipData() != null) {
                // Múltiplos arquivos
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    arquivos.add(new ArquivoUpload(uri, obterNome(uri)));
                }
            } else if (data.getData() != null) {
                // Arquivo único
                Uri uri = data.getData();
                arquivos.add(new ArquivoUpload(uri, obterNome(uri)));
            }
        } else if (requestCode == REQ_UPLOAD_FOLDER) {
            // Pasta inteira
            Uri treeUri = data.getData();
            if (treeUri != null) {
                DocumentFile raiz = DocumentFile.fromTreeUri(this, treeUri);
                if (raiz != null && raiz.isDirectory()) {
                    // O nome da pasta selecionada será usado como prefixo
                    varrerPasta(raiz, raiz.getName() != null ? raiz.getName() : "", arquivos);
                }
            }
        }

        if (!arquivos.isEmpty()) {
            iniciarUploadLote(arquivos);
        }
    }

    private void varrerPasta(DocumentFile dir, String caminhoAtualStr, List<ArquivoUpload> lista) {
        for (DocumentFile f : dir.listFiles()) {
            String nome = f.getName();
            if (nome == null) continue;
            String novoCaminho = caminhoAtualStr.isEmpty() ? nome : caminhoAtualStr + "/" + nome;
            
            if (f.isDirectory()) {
                varrerPasta(f, novoCaminho, lista);
            } else if (f.isFile()) {
                lista.add(new ArquivoUpload(f.getUri(), novoCaminho));
            }
        }
    }

    private void iniciarUploadLote(List<ArquivoUpload> arquivos) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("Enviando arquivos");
        progress.setMessage("Preparando...");
        progress.setCancelable(false);
        progress.show();
        enviarProximo(arquivos, 0, progress);
    }

    private void enviarProximo(List<ArquivoUpload> arquivos, int index, ProgressDialog progress) {
        if (index >= arquivos.size()) {
            progress.dismiss();
            Toast.makeText(this, "Upload concluído com sucesso!", Toast.LENGTH_LONG).show();
            carregar();
            return;
        }

        ArquivoUpload arq = arquivos.get(index);
        progress.setMessage("Enviando " + (index + 1) + "/" + arquivos.size() + "\n" + arq.caminhoRelativo);

        String caminhoCompleto = caminhoAtual.isEmpty() ? arq.caminhoRelativo : caminhoAtual + "/" + arq.caminhoRelativo;

        try {
            byte[] bytes = lerBytes(arq.uri);

            // Verifica se o arquivo já existe para pegar o SHA (para sobrescrever)
            cliente.obterShaArquivo(dono, repo, caminhoCompleto, new ClienteGitHub.Callback<String>() {
                @Override
                public void sucesso(String sha) {
                    // Arquivo existe, envia com SHA
                    cliente.salvarArquivoBytes(dono, repo, caminhoCompleto, bytes, sha, "Atualizar " + arq.caminhoRelativo, new ClienteGitHub.Callback<JSONObject>() {
                        @Override public void sucesso(JSONObject o) { enviarProximo(arquivos, index + 1, progress); }
                        @Override public void erro(String m) { progress.dismiss(); mostrarErro(m); }
                    });
                }
                @Override
                public void erro(String m) {
                    // Arquivo não existe, envia sem SHA
                    cliente.salvarArquivoBytes(dono, repo, caminhoCompleto, bytes, null, "Upload " + arq.caminhoRelativo, new ClienteGitHub.Callback<JSONObject>() {
                        @Override public void sucesso(JSONObject o) { enviarProximo(arquivos, index + 1, progress); }
                        @Override public void erro(String m) { progress.dismiss(); mostrarErro(m); }
                    });
                }
            });
        } catch (Exception e) {
            progress.dismiss();
            mostrarErro("Erro ao ler arquivo: " + e.getMessage());
        }
    }

    private void mostrarErro(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private String obterNome(Uri uri) {
        String nome = "arquivo";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index != -1) nome = cursor.getString(index);
            }
        } catch (Exception ignored) {}
        return nome;
    }

    private byte[] lerBytes(Uri uri) throws Exception {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) throw new Exception("Não foi possível abrir o arquivo");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }
}