package tw.bookprice.seed;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Runs the catalogue seed once at startup.
 *
 * This is a separate bean on purpose. Calling a @Transactional method from
 * another method of the same class goes straight to the target instance and
 * skips the proxy, so the seed would commit one 作品 at a time; a failure
 * part-way through would leave a half-filled catalogue that the count guard in
 * CatalogueSeeder then treats as already seeded, permanently. Going through an
 * injected reference keeps the whole seed in one transaction.
 */
@Component
public class SeedRunner implements CommandLineRunner {

    private final CatalogueSeeder seeder;

    public SeedRunner(CatalogueSeeder seeder) {
        this.seeder = seeder;
    }

    @Override
    public void run(String... args) {
        seeder.seed();
    }
}
