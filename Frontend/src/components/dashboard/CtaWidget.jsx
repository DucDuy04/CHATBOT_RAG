import { Rocket } from 'lucide-react';

export default function CtaWidget() {
  return (
    <div className="bg-blue-600 rounded-2xl p-8 shadow-md relative overflow-hidden h-full flex flex-col justify-center">
      {/* Background Icon (Watermark) */}
      <Rocket className="absolute -bottom-6 -right-6 text-blue-500 opacity-30 w-48 h-48 rotate-12" strokeWidth={1} />
      
      <div className="relative z-10">
        <h3 className="text-xl font-bold text-white mb-2">Need a new Chatbot?</h3>
        <p className="text-blue-100 text-sm mb-6 max-w-sm leading-relaxed">
          Deploy a new RAG-powered assistant in minutes using your knowledge base files.
        </p>
        <button className="bg-white text-blue-600 font-bold text-sm px-6 py-2.5 rounded-full shadow-sm hover:bg-gray-50 hover:shadow-md transition-all">
          Start Deployment
        </button>
      </div>
    </div>
  );
}