package app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {

    static final String PREFS = "gitigin";
    private ClienteGitHub cliente;
    private ListView lista;
    private JSONArray repos = new JSONArray();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String token = prefs.getString("token", null);
        if (token == null) telaLogin(); else iniciar(token);
    }

    // ---------- Login ----------
    private void telaLogin() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(60, 120, 60, 60);

        TextView t = new TextView(this);
        t.setText("Gitigin Studio");
        t.setTextSize(30);
        root.addView(t);

        TextView d = new TextView(this);
        d.setText("\nCole seu Personal Access Token (escopo 'repo').\nCrie em: github.com/settings/tokens\n");
        root.addView(d);

        EditText e = new EditText(this);
        e.setHint("ghp_...");
        root.addView(e);

        Button b = new Button(this);
        b.setText("Entrar");
        root.addView(b);

        b.setOnClickListener(v -> {
            String tk = e.getText().toString().trim();
            if (tk.isEmpty()) return;
            b.setEnabled(false); b.setText("Validando...");
            new ClienteGitHub(tk).obterUsuario(new ClienteGitHub.Callback<JSONObject>() {
                @Override public void sucesso(JSONObject o) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("token", tk).apply();
                    iniciar(tk);
                }
                @Override public void erro(String m) {
                    b.setEnabled(true); b.setText("Entrar");
                    Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show();
                }
            });
        });

        setContentView(root);
    }

    // ---------- Principal ----------
    private void iniciar(String token) {
        cliente = new ClienteGitHub(token);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout barra = new LinearLayout(this);
        barra.setOrientation(LinearLayout.HORIZONTAL);
        barra.setPadding(20, 20, 20, 20);

        TextView titulo = new TextView(this);
        titulo.setText("Meus repositórios");
        titulo.setTextSize(20);
        titulo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        barra.addView(titulo);

        Button novo = new Button(this); novo.setText("+ NOVO"); barra.addView(novo);
        Button sair = new Button(this); sair.setText("SAIR");   barra.addView(sair);
        root.addView(barra);

        lista = new ListView(this);
        lista.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(lista);

        lista.setOnItemClickListener((p, v, pos, id) -> {
            try {
                JSONObject r = repos.getJSONObject(pos);
                Intent i = new Intent(this, AtividadeRepositorio.class);
                i.putExtra("token", token);
                i.putExtra("dono", r.getJSONObject("owner").getString("login"));
                i.putExtra("repo", r.getString("name"));
                startActivity(i);
            } catch (Exception ignored) {}
        });

        lista.setOnItemLongClickListener((p, v, pos, id) -> {
            try {
                JSONObject r = repos.getJSONObject(pos);
                String nome = r.getString("name");
                String dono = r.getJSONObject("owner").getString("login");
                String desc = r.optString("description", "");
                boolean priv = r.optBoolean("private", false);
                new AlertDialog.Builder(this)
                        .setTitle(nome)
                        .setItems(new String[]{"Editar", "Excluir"}, (d, w) -> {
                            if (w == 0) dialogoEditar(dono, nome, desc, priv);
                            else        dialogoExcluir(dono, nome);
                        }).show();
            } catch (Exception ignored) {}
            return true;
        });

        novo.setOnClickListener(v -> dialogoCriar());
        sair.setOnClickListener(v -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove("token").apply();
            recreate();
        });

        setContentView(root);
        carregarRepos();
    }

    private void carregarRepos() {
        cliente.listarRepositorios(new ClienteGitHub.Callback<JSONArray>() {
            @Override public void sucesso(JSONArray arr) {
                repos = arr;
                
                // Usando BaseAdapter em vez de ArrayAdapter
                BaseAdapter ad = new BaseAdapter() {
                    @Override public int getCount() { return repos.length(); }
                    @Override public Object getItem(int pos) { return pos; }
                    @Override public long getItemId(int pos) { return pos; }
                    
                    @Override public View getView(int pos, View cv, ViewGroup parent) {
                        View v = cv;
                        if (v == null) {
                            v = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, parent, false);
                        }
                        TextView t1 = v.findViewById(android.R.id.text1);
                        TextView t2 = v.findViewById(android.R.id.text2);
                        try {
                            JSONObject r = repos.getJSONObject(pos);
                            t1.setText(r.getString("name") + (r.optBoolean("private") ? " 🔒" : ""));
                            String d = r.isNull("description") ? "" : r.optString("description");
                            t2.setText(d.isEmpty() ? "—" : d);
                        } catch (Exception ignored) {}
                        return v;
                    }
                };
                lista.setAdapter(ad);
            }
            @Override public void erro(String m) {
                Toast.makeText(MainActivity.this, "Erro: " + m, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void dialogoCriar() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(40, 20, 40, 20);
        EditText nome = new EditText(this); nome.setHint("Nome do repositório"); l.addView(nome);
        EditText desc = new EditText(this); desc.setHint("Descrição (opcional)"); l.addView(desc);
        CheckBox priv = new CheckBox(this); priv.setText("Privado"); l.addView(priv);

        new AlertDialog.Builder(this)
                .setTitle("Novo repositório")
                .setView(l)
                .setPositiveButton("Criar", (d, w) -> {
                    String n = nome.getText().toString().trim();
                    if (n.isEmpty()) return;
                    cliente.criarRepositorio(n, desc.getText().toString(), priv.isChecked(),
                            new ClienteGitHub.Callback<JSONObject>() {
                                @Override public void sucesso(JSONObject o) { carregarRepos(); }
                                @Override public void erro(String m) {
                                    Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void dialogoEditar(String dono, String nome, String descAtual, boolean privAtual) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(40, 20, 40, 20);
        EditText desc = new EditText(this); desc.setText(descAtual); l.addView(desc);
        CheckBox priv = new CheckBox(this); priv.setText("Privado"); priv.setChecked(privAtual); l.addView(priv);

        new AlertDialog.Builder(this)
                .setTitle("Editar " + nome)
                .setView(l)
                .setPositiveButton("Salvar", (d, w) -> cliente.editarRepositorio(
                        dono, nome, desc.getText().toString(), priv.isChecked(),
                        new ClienteGitHub.Callback<JSONObject>() {
                            @Override public void sucesso(JSONObject o) { carregarRepos(); }
                            @Override public void erro(String m) {
                                Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton("Cancelar", null).show();
    }

    private void dialogoExcluir(String dono, String nome) {
        new AlertDialog.Builder(this)
                .setTitle("Excluir " + nome + "?")
                .setMessage("Essa ação é irreversível.")
                .setPositiveButton("Excluir", (d, w) -> cliente.deletarRepositorio(
                        dono, nome, new ClienteGitHub.Callback<Boolean>() {
                            @Override public void sucesso(Boolean ok) { carregarRepos(); }
                            @Override public void erro(String m) {
                                Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton("Cancelar", null).show();
    }
}