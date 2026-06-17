import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Programa cliente para testar SynchronizedRGB.
 * Demonstra uso correto e riscos de condições de corrida
 * quando operações compostas não são sincronizadas externamente.
 */
public class SynchronizedRGBClient {

    // ------------------------------------------------------------------ //
    //  1. Teste básico de criação e leitura                               //
    // ------------------------------------------------------------------ //
    static void testeBasico() {
        System.out.println("=== Teste Básico ===");
        SynchronizedRGB cor = new SynchronizedRGB(100, 150, 200, "Azul Claro");

        System.out.println("Nome   : " + cor.getName());
        System.out.printf ("RGB hex: #%06X%n", cor.getRGB());

        cor.invert();
        System.out.println("Após invert() -> Nome: " + cor.getName());
        System.out.printf ("RGB hex: #%06X%n%n", cor.getRGB());
    }

    // ------------------------------------------------------------------ //
    //  2. Teste de set() com validação de intervalo                       //
    // ------------------------------------------------------------------ //
    static void testeValidacao() {
        System.out.println("=== Teste de Validação ===");
        SynchronizedRGB cor = new SynchronizedRGB(0, 0, 0, "Preto");

        try {
            cor.set(300, 0, 0, "Vermelho Inválido");
            System.out.println("ERRO: deveria ter lançado exceção!");
        } catch (IllegalArgumentException e) {
            System.out.println("OK: IllegalArgumentException capturada para valor 300.");
        }

        try {
            cor.set(-1, 200, 50, "Verde Inválido");
        } catch (IllegalArgumentException e) {
            System.out.println("OK: IllegalArgumentException capturada para valor -1.");
        }

        System.out.println();
    }

    // ------------------------------------------------------------------ //
    //  3. Teste de condição de corrida em operação composta               //
    //     getName() + getRGB() NÃO são atômicas juntas sem lock externo.  //
    // ------------------------------------------------------------------ //
    static void testeCondicaoCorrida() throws InterruptedException {
        System.out.println("=== Teste de Condição de Corrida (operação composta) ===");
        SynchronizedRGB cor = new SynchronizedRGB(255, 0, 0, "Vermelho");

        // Thread escritora: alterna entre Vermelho e Azul rapidamente
        Thread escritora = new Thread(() -> {
            for (int i = 0; i < 200_000; i++) {
                if (i % 2 == 0)
                    cor.set(255, 0, 0, "Vermelho");
                else
                    cor.set(0, 0, 255, "Azul");
            }
        });

        // Thread leitora: lê nome e RGB sem lock externo — inconsistência possível
        Thread leitora = new Thread(() -> {
            int inconsistencias = 0;
            for (int i = 0; i < 200_000; i++) {
                String nome = cor.getName();   // leitura 1
                int rgb    = cor.getRGB();     // leitura 2  ← escritora pode agir aqui!

                boolean nomeVermelho = nome.equals("Vermelho");
                boolean rgbVermelho  = (rgb == ((255 << 16) | (0 << 8) | 0));

                if (nomeVermelho != rgbVermelho) inconsistencias++;
            }
            System.out.println("Inconsistências detectadas (sem lock externo): " + inconsistencias);
        });

        escritora.start();
        leitora.start();
        escritora.join();
        leitora.join();

        // Solução correta: sincronizar externamente a operação composta
        System.out.println("Leitura consistente (com lock externo):");
        synchronized (cor) {
            System.out.println("  Nome: " + cor.getName());
            System.out.printf ("  RGB : #%06X%n", cor.getRGB());
        }
        System.out.println();
    }

    // ------------------------------------------------------------------ //
    //  4. Teste de concorrência com pool de threads                       //
    // ------------------------------------------------------------------ //
    static void testeConcorrencia() throws InterruptedException {
        System.out.println("=== Teste de Concorrência (pool de threads) ===");
        SynchronizedRGB cor = new SynchronizedRGB(128, 128, 128, "Cinza");
        ExecutorService pool = Executors.newFixedThreadPool(8);

        for (int t = 0; t < 8; t++) {
            final int id = t;
            pool.submit(() -> {
                for (int i = 0; i < 10; i++) {
                    int r = (id * 30) % 256;
                    int g = (id * 20 + i * 5) % 256;
                    int b = (id * 10 + i * 10) % 256;
                    cor.set(r, g, b, "Thread-" + id + "-iter-" + i);
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("Estado final após 8 threads x 10 escritas:");
        synchronized (cor) {
            System.out.println("  Nome: " + cor.getName());
            System.out.printf ("  RGB : #%06X%n%n", cor.getRGB());
        }
    }

    // ------------------------------------------------------------------ //
    //  main                                                               //
    // ------------------------------------------------------------------ //
    public static void main(String[] args) throws InterruptedException {
        testeBasico();
        testeValidacao();
        testeCondicaoCorrida();
        testeConcorrencia();
        System.out.println("Todos os testes de SynchronizedRGB concluídos.");
    }
}