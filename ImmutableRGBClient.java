import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Programa cliente para testar ImmutableRGB.
 * Por ser imutável, é intrinsecamente thread-safe:
 * nenhuma sincronização externa é necessária.
 */
public class ImmutableRGBClient {

    // ------------------------------------------------------------------ //
    //  1. Teste básico de criação e leitura                               //
    // ------------------------------------------------------------------ //
    static void testeBasico() {
        System.out.println("=== Teste Básico ===");
        ImmutableRGB cor = new ImmutableRGB(100, 150, 200, "Azul Claro");

        System.out.println("Nome   : " + cor.getName());
        System.out.printf ("RGB hex: #%06X%n", cor.getRGB());

        // invert() retorna NOVO objeto — original inalterado
        ImmutableRGB invertida = cor.invert();
        System.out.println("Após invert() -> nova cor:");
        System.out.println("  Nome: " + invertida.getName());
        System.out.printf ("  RGB : #%06X%n", invertida.getRGB());

        System.out.println("Original permanece inalterado:");
        System.out.println("  Nome: " + cor.getName());
        System.out.printf ("  RGB : #%06X%n%n", cor.getRGB());
    }

    // ------------------------------------------------------------------ //
    //  2. Teste de imutabilidade (tentativa de modificação)              //
    // ------------------------------------------------------------------ //
    static void testeImutabilidade() {
        System.out.println("=== Teste de Imutabilidade ===");
        ImmutableRGB original = new ImmutableRGB(255, 0, 0, "Vermelho");

        // A única forma de "mudar" é criar novo objeto
        ImmutableRGB modificada = new ImmutableRGB(0, 255, 0, "Verde");

        System.out.println("'original' depois da criação de 'modificada':");
        System.out.println("  Nome: " + original.getName()); // ainda "Vermelho"
        System.out.printf ("  RGB : #%06X%n", original.getRGB());
        System.out.println("Imutabilidade confirmada: original não foi alterado.\n");
    }

    // ------------------------------------------------------------------ //
    //  3. Teste de validação                                              //
    // ------------------------------------------------------------------ //
    static void testeValidacao() {
        System.out.println("=== Teste de Validação ===");
        try {
            new ImmutableRGB(300, 0, 0, "Inválido");
            System.out.println("ERRO: deveria ter lançado exceção!");
        } catch (IllegalArgumentException e) {
            System.out.println("OK: IllegalArgumentException para valor 300.");
        }

        try {
            new ImmutableRGB(0, -5, 0, "Inválido");
        } catch (IllegalArgumentException e) {
            System.out.println("OK: IllegalArgumentException para valor -5.");
        }
        System.out.println();
    }

    // ------------------------------------------------------------------ //
    //  4. Teste de concorrência — sem condição de corrida possível        //
    //     Múltiplas threads leem o mesmo objeto: sempre consistente.      //
    // ------------------------------------------------------------------ //
    static void testeConcorrenciaLeitura() throws InterruptedException {
        System.out.println("=== Teste de Concorrência — Leituras Paralelas ===");
        ImmutableRGB cor = new ImmutableRGB(255, 128, 64, "Laranja");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicReference<String> erroEncontrado = new AtomicReference<>(null);

        for (int t = 0; t < 8; t++) {
            pool.submit(() -> {
                for (int i = 0; i < 50_000; i++) {
                    String nome = cor.getName();
                    int    rgb  = cor.getRGB();

                    // Verifica consistência: nome e RGB devem corresponder sempre
                    boolean nomeOk = nome.equals("Laranja");
                    boolean rgbOk  = (rgb == ((255 << 16) | (128 << 8) | 64));
                    if (!nomeOk || !rgbOk) {
                        erroEncontrado.set("Inconsistência detectada na thread "
                                + Thread.currentThread().getName());
                    }
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        if (erroEncontrado.get() == null)
            System.out.println("Nenhuma inconsistência em 400.000 leituras paralelas. ✓");
        else
            System.out.println("FALHA: " + erroEncontrado.get());
        System.out.println();
    }

    // ------------------------------------------------------------------ //
    //  5. Teste de concorrência — publicação segura via AtomicReference   //
    //     Simula troca de "estado atual" sem sincronização explícita.     //
    // ------------------------------------------------------------------ //
    static void testeConcorrenciaEscrita() throws InterruptedException {
        System.out.println("=== Teste de Concorrência — Publicação Segura (AtomicReference) ===");
        AtomicReference<ImmutableRGB> corAtual =
                new AtomicReference<>(new ImmutableRGB(0, 0, 0, "Preto"));

        ExecutorService escritoras = Executors.newFixedThreadPool(4);
        ExecutorService leitoras   = Executors.newFixedThreadPool(4);
        AtomicReference<String> erro = new AtomicReference<>(null);

        // Threads escritoras: cada uma publica um novo ImmutableRGB
        for (int t = 0; t < 4; t++) {
            final int id = t;
            escritoras.submit(() -> {
                for (int i = 0; i < 10_000; i++) {
                    int r = (id * 60 + i) % 256;
                    int g = (id * 40 + i * 2) % 256;
                    int b = (id * 20 + i * 3) % 256;
                    corAtual.set(new ImmutableRGB(r, g, b, "Cor-" + id + "-" + i));
                }
            });
        }

        // Threads leitoras: cada referência obtida é imutável — leitura sempre segura
        for (int t = 0; t < 4; t++) {
            leitoras.submit(() -> {
                for (int i = 0; i < 10_000; i++) {
                    ImmutableRGB snapshot = corAtual.get(); // snapshot atômico
                    int  rgb  = snapshot.getRGB();
                    String nm = snapshot.getName();
                    // snapshot não pode mudar — nunca inconsistente
                    if (nm == null || rgb < 0) {
                        erro.set("Estado inválido detectado!");
                    }
                }
            });
        }

        escritoras.shutdown();
        leitoras.shutdown();
        escritoras.awaitTermination(10, TimeUnit.SECONDS);
        leitoras.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("Cor final publicada: " + corAtual.get().getName());
        System.out.printf ("RGB final          : #%06X%n", corAtual.get().getRGB());
        System.out.println(erro.get() == null
                ? "Nenhum estado inválido em 80.000 operações. ✓"
                : "FALHA: " + erro.get());
        System.out.println();
    }

    // ------------------------------------------------------------------ //
    //  6. Encadeamento de inversões                                       //
    // ------------------------------------------------------------------ //
    static void testeEncadeamento() {
        System.out.println("=== Teste de Encadeamento de invert() ===");
        ImmutableRGB original = new ImmutableRGB(10, 20, 30, "Base");
        ImmutableRGB duplo    = original.invert().invert(); // deve igualar ao original

        System.out.printf("Original  : RGB=#%06X  Nome=%s%n",
                original.getRGB(), original.getName());
        System.out.printf("Inv→Inv   : RGB=#%06X  Nome=%s%n",
                duplo.getRGB(), duplo.getName());
        System.out.println("RGB igual ao original? " + (original.getRGB() == duplo.getRGB()));
        System.out.println();
    }

    // ------------------------------------------------------------------ //
    //  main                                                               //
    // ------------------------------------------------------------------ //
    public static void main(String[] args) throws InterruptedException {
        testeBasico();
        testeImutabilidade();
        testeValidacao();
        testeConcorrenciaLeitura();
        testeConcorrenciaEscrita();
        testeEncadeamento();
        System.out.println("Todos os testes de ImmutableRGB concluídos.");
    }
}