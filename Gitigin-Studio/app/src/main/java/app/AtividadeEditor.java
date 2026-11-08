package app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONObject;

public class AtividadeEditor extends Activity {

    private ClienteGitHub cliente;
    private String dono, repo, caminho, sha = null;
    private EditText campoConteudo, campoMensagem;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String token = getIntent().getStringExtra("token");
        dono     = getIntent().getStringExtra("dono");
        repo     = getIntent().getStringExtra("repo");
        caminho  = getIntent().getStringExtra("caminho");
        boolean novo = getIntent().getBooleanExtra("novo", false);
        cliente = new ClienteGitHub(token);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 20);

        TextView titulo = new TextView(this);
        titulo.setText(caminho);
        titulo.setTextSize(16);
        root.addView(titulo);

        campoConteudo = new EditText(this);
        campoConteudo.setGravity(Gravity.TOP | Gravity.START);
        campoConteudo.setTypeface(Typeface.MONOSPACE);
        campoConteudo.setHorizontallyScrolling(false);
        campoConteudo.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(campoConteudo);

        campoMensagem = new EditText(this);
        campoMensagem.setHint("Mensagem do commit");
        campoMensagem.setText("Atualizar " + caminho);
        root.addView(campoMensagem);

        LinearLayout botoes = new LinearLayout(this);
        botoes.setOrientation(LinearLayout.HORIZONTAL);

        Button salvar = new Button(this);
        salvar.setText("Commitar");
        salvar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button apagar = new Button(this);
        apagar.setText("Apagar");
        apagar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        botoes.addView(salvar);
        botoes.addView(apagar);
        root.addView(botoes);

        salvar.setOnClickListener(v -> salvar());
        apagar.setOnClickListener(v -> apagar());

        setContentView(root);

        if (!novo) carregar(); else apagar.setEnabled(false);
    }

    private void carregar() {
        cliente.obterArquivo(dono, repo, caminho, new ClienteGitHub.Callback<ClienteGitHub.Arquivo>() {
            @Override public void sucesso(ClienteGitHub.Arquivo a) {
                sha = a.sha;
                campoConteudo.setText(a.conteudo);
            }
            @Override public void erro(String m) {
                Toast.makeText(AtividadeEditor.this, m, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void salvar() {
        String msg = campoMensagem.getText().toString();
        if (msg.isEmpty()) msg = "Atualizar " + caminho;
        cliente.salvarArquivo(dono, repo, caminho, campoConteudo.getText().toString(),
                sha, msg, new ClienteGitHub.Callback<JSONObject>() {
                    @Override public void sucesso(JSONObject o) {
                        Toast.makeText(AtividadeEditor.this, "Commit enviado!", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                    @Override public void erro(String m) {
                        Toast.makeText(AtividadeEditor.this, "Erro: " + m, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void apagar() {
        if (sha == null) { finish(); return; }
        new AlertDialog.Builder(this)
                .setTitle("Apagar arquivo?")
                .setPositiveButton("Apagar", (d, w) -> cliente.deletarArquivo(
                        dono, repo, caminho, sha, "Apagar " + caminho,
                        new ClienteGitHub.Callback<Boolean>() {
                            @Override public void sucesso(Boolean ok) { finish(); }
                            @Override public void erro(String m) {
                                Toast.makeText(AtividadeEditor.this, m, Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton("Cancelar", null).show();
    }
}