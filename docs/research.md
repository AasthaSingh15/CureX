# Research and verified choices

The backend pins Spring Boot **3.5.16** and targets Java **17**. Spring Boot's system requirements say 3.5.16 needs Java 17+ and supports Java versions through 25, so the locally installed Java 24 can run it while the source remains LTS-targeted. Maven 3.6.3+ is required by that same source.

* [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
* [Spring Data JPA reference](https://docs.spring.io/spring-data/jpa/reference/jpa.html)
* [Spring Security authentication](https://docs.spring.io/spring-security/reference/features/authentication/)
* [Spring Security authorization](https://docs.spring.io/spring-security/reference/servlet/authorization/index.html)
* [Spring scheduling reference](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
* [Spring validation reference](https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html)
* [React documentation](https://react.dev/learn)
* [Vite guide](https://vite.dev/guide/)
* [PostgreSQL documentation](https://www.postgresql.org/docs/)
* [Supabase: connect to Postgres](https://supabase.com/docs/guides/database/connecting-to-postgres)
* [Supabase Spring Boot quickstart](https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot)
* [Flyway getting started](https://documentation.red-gate.com/flyway/getting-started-with-flyway)

The workflow is implemented as a small, explicit domain transition table rather than Spring Statemachine. This avoids introducing an additional framework when the domain has a fixed, auditable transition graph. `Workflow.java` is the single transition authority and the API exposes its legal actions.
