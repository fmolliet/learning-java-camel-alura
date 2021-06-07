package br.com.caelum.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.impl.DefaultCamelContext;

public class RotaPedidos {

	public static void main(String[] args) throws Exception {

		CamelContext context = new DefaultCamelContext();
		
		context.addRoutes(new RouteBuilder() {
			
			// Vamos usar os dados e definir destino e 
			@Override
			public void configure() throws Exception {
				
				// Utilizamos subrotas
				from("file:pedidos?delay=5s&noop=true")
					.routeId("rota-pedidos")  // Melhora legibilidade indicando o nome
					.multicast()  // O método multicast() permite que a mesma mensagem seja enviada para vários endpoints.
						.to("direct:http") // Separa as subrotas direct
						.to("direct:soap"); // Em vez de direct conseguimos usar seda que faz de maneira assincrona (Staged event-driven architecture)
				
				
				from("direct:http")
					.routeId("rota-Http") 
					.setProperty("pedidoId", xpath("/pedido/id/text()")) // Atribuibos as properties 
					.setProperty("clienteId", xpath("/pedido/pagamento/email-titular/text()"))
					.split() // divide o conteudo
						.xpath("/pedido/itens/item") // Separa pelos items do pedido
					.filter() // filtra o conteudo
						.xpath("/item/formato[text()='EBOOK']") // usa o xpath para buscar o texto do formato 
						// Vamos tirar o pedido/itens pois depois que ele dividiu formou um novo objeto
					.setProperty("ebookId", xpath("/item/livro/codigo/text()")) // Atribuibos as properties 
					.marshal().xmljson() // transforma xml em JSON ou dados em memoria para outro formado e o contrario é unmarshal
					.log("${id} - ${body}")
					.setHeader(Exchange.HTTP_METHOD, HttpMethods.GET) 
					.setHeader(Exchange.HTTP_QUERY, simple("ebookId=${property.ebookId}&pedidoId=${property.pedidoId}&clienteId=${property.clienteId}")) 
					//.setHeader(Exchange.FILE_NAME, simple("${file:name.noext}-${header.CamelSplitIndex}.json")) // para usar nomes constants só usar metodo constant
				.to("http4://localhost:8080/webservices/ebook/item");
				
				
				from("direct:soap")
					.routeId("rota-soap") // Melhora legibilidade
					.to("xslt:pedido-para-soap.xslt")
					//.transform(body().regexReplaceAll("tipo", "tipoEntrada")) // Para fazer replace dos dados
					.log("${body}")
					.setHeader(Exchange.CONTENT_TYPE, constant("text/xml")) 
				.to("http4://localhost:8080/webservices/financeiro");
				//.to("mock:soap"); // para mockar o output
			}
			
		});
		
		context.start();
		Thread.sleep(20000);
		context.stop();
	}	
}
